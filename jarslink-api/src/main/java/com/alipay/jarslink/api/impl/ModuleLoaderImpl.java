/*
 *
 *  * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *      http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */
package com.alipay.jarslink.api.impl;

import com.alipay.jarslink.api.ApplicationContextPostProcessor;
import com.alipay.jarslink.api.Module;
import com.alipay.jarslink.api.ModuleAware;
import com.alipay.jarslink.api.ModuleConfig;
import com.alipay.jarslink.api.ModuleListenerDispatcher;
import com.alipay.jarslink.api.ModuleLoader;
import com.alipay.jarslink.api.ModulePostProcessor;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Iterables.toArray;

/**
 * 模块加载器实现
 *
 * @author tengfei.fangtf
 * @version $Id: ModuleLoaderImpl.java, v 0.1 2017-4-24 上午9:58:12 tengfei.fangtf Exp $
 */
public class ModuleLoaderImpl implements ModuleLoader, ApplicationContextAware {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModuleLoaderImpl.class);

    /**
     * Spring bean文件所在目录,不同的路径确保能取到资源
     */
    private static String[] SPRING_XML_PATTERN = {"classpath*:META-INF/spring/*.xml", "classpath*:*META-INF/spring/*.xml"};

    /**
     * 模块版本属性
     */
    private static final String MODULE_PROPERTY_VERSION = "module_version";

    /**
     * 模块名属性
     */
    private static final String MODULE_PROPERTY_NAME = "module_name";

    /**
     * 不加载的Spring配置文件
     */
    private static final String MODULE_EXCLUSION_CONFIGE_NAME = "exclusion_confige_name";

    /**
     * 注入父applicationContext
     */
    private ApplicationContext applicationContext;

    private final List<com.alipay.jarslink.api.ApplicationContextAware> applicationContextAwares = new
            CopyOnWriteArrayList<com.alipay.jarslink.api.ApplicationContextAware>();
    private final List<ModuleAware> moduleAwares = new CopyOnWriteArrayList<ModuleAware>();
    private final List<ApplicationContextPostProcessor> applicationContextPostProcessors = new
            CopyOnWriteArrayList<ApplicationContextPostProcessor>();
    private final List<ModulePostProcessor> modulePostProcessors = new CopyOnWriteArrayList<ModulePostProcessor>();

    private ModuleListenerDispatcher listenerDispatcher;

    @Override
    public Module load(ModuleConfig moduleConfig) {
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Loading module: {}", moduleConfig);
        }
        List<String> tempFileJarURLs = moduleConfig.getModuleUrlPath();
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Local jars: {}", tempFileJarURLs);
        }

        doModuleAware(moduleConfig);

        ConfigurableApplicationContext moduleApplicationContext = loadModuleApplication(moduleConfig, tempFileJarURLs);

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Loading module  complete：{}", moduleConfig);
        }
        Module module = new SpringModule(moduleConfig, moduleConfig.getVersion(), moduleConfig.getName(),
                moduleApplicationContext);
        doModulePostProcessor(module, moduleConfig);
        onLoaded(module);
        return module;
    }

    @Override
    public void unload(Module module) {
        if (module != null) {
            onPreDestroy(module);
            module.destroy();
        }
    }

    @Override
    public void registerAware(com.alipay.jarslink.api.ApplicationContextAware aware) {
        checkArgument(aware != null, "aware must be not null");
        applicationContextAwares.add(aware);
    }

    @Override
    public void registerAware(ModuleAware aware) {
        checkArgument(aware != null, "aware must be not null");
        moduleAwares.add(aware);
    }

    @Override
    public void registerPostProcessor(ApplicationContextPostProcessor postProcessor) {
        checkArgument(postProcessor != null, "PostProcessor must be not null");
        applicationContextPostProcessors.add(postProcessor);
    }

    @Override
    public void registerPostProcessor(ModulePostProcessor postProcessor) {
        checkArgument(postProcessor != null, "PostProcessor must be not null");
        modulePostProcessors.add(postProcessor);
    }

    @Override
    public void unRegisterAware(com.alipay.jarslink.api.ApplicationContextAware aware) {
        if (aware == null) return;
        applicationContextAwares.remove(aware);
    }

    @Override
    public void unRegisterAware(ModuleAware aware) {
        if (aware == null) return;
        moduleAwares.remove(aware);
    }

    @Override
    public void unRegisterPostProcessor(ApplicationContextPostProcessor postProcessor) {
        if (postProcessor == null) return;
        applicationContextPostProcessors.remove(postProcessor);
    }

    @Override
    public void unRegisterPostProcessor(ModulePostProcessor postProcessor) {
        if (postProcessor == null) return;
        modulePostProcessors.remove(postProcessor);
    }

    private void doApplicationContextAware(ConfigurableApplicationContext context, ModuleConfig moduleConfig) {
        for (com.alipay.jarslink.api.ApplicationContextAware aware : applicationContextAwares) {
            aware.setConfigurableApplicationContext(context, moduleConfig);
        }
    }

    private void doModuleAware(ModuleConfig moduleConfig) {
        for (ModuleAware aware : moduleAwares) {
            aware.setModuleConfig(moduleConfig);
        }
    }

    private void doApplicationContextPostProcessor(ConfigurableApplicationContext context, ModuleConfig moduleConfig) {
        for (ApplicationContextPostProcessor postProcessor : applicationContextPostProcessors) {
            postProcessor.setConfigurableApplicationContext(context, moduleConfig);
        }
    }

    private void doModulePostProcessor(Module module, ModuleConfig moduleConfig) {
        for (ModulePostProcessor postProcessor : modulePostProcessors) {
            postProcessor.setModule(module, moduleConfig);
        }
    }

    /**
     * 根据本地临时文件Jar，初始化模块自己的ClassLoader，初始化Spring Application Context，同时要设置当前线程上下文的ClassLoader问模块的ClassLoader
     *
     * @param moduleConfig
     * @param tempFileJarURLs
     * @return
     */
    private ConfigurableApplicationContext loadModuleApplication(ModuleConfig moduleConfig, List<String>
            tempFileJarURLs) {
        ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
        //获取模块的ClassLoader
        ClassLoader moduleClassLoader = new ModuleClassLoader(moduleConfig.getModuleUrl(), applicationContext.getClassLoader(),
                getOverridePackages(moduleConfig));

        try {
            //把当前线程的ClassLoader切换成模块的
            Thread.currentThread().setContextClassLoader(moduleClassLoader);

            ConfigurableApplicationContext context;
            Properties properties = getProperties(moduleConfig);
            Set<String> scanBase = moduleConfig.getScanPackages();
            //注解方式加载bean
            if (!scanBase.isEmpty()) {
                ModuleAnnotationApplicationContext annotationConfigApplicationContext = new
                        ModuleAnnotationApplicationContext(properties);
                annotationConfigApplicationContext.scan(scanBase.toArray(new String[0]));
                context = annotationConfigApplicationContext;
            } else {
                //XML方式加载bean
                ModuleXmlApplicationContext moduleApplicationContext = new ModuleXmlApplicationContext();
                moduleApplicationContext.setProperties(properties);
                String[] configs = findSpringConfigs(tempFileJarURLs, moduleClassLoader, getExclusionConfigeNameList(properties));
                moduleApplicationContext.setConfigLocations(configs);
                context = moduleApplicationContext;
            }
            context.setParent(applicationContext);
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("module {}:{} allow current process to override bean in module", moduleConfig.getName(),
                        moduleConfig.getVersion());
            }
            ((DefaultResourceLoader) context).setClassLoader(moduleClassLoader);
            doApplicationContextAware(context, moduleConfig);
            context.refresh();
            doApplicationContextPostProcessor(context, moduleConfig);
            return context;
        } catch (Throwable e) {
            CachedIntrospectionResults.clearClassLoader(moduleClassLoader);
            throw Throwables.propagate(e);
        } finally {
            //还原当前线程的ClassLoader
            Thread.currentThread().setContextClassLoader(currentClassLoader);
        }
    }

    /**
     * 获取不加载的spring配置文件名称
     *
     * @param properties
     * @return
     */
    private List<String> getExclusionConfigeNameList(Properties properties) {
        String property = properties.getProperty(MODULE_EXCLUSION_CONFIGE_NAME);
        if (property != null) {
            return Lists.newArrayList(property.split(","));
        }
        return Lists.newArrayList();

    }

    /**
     * 获得模块的配置，并会增加一些模块的常量信息
     *
     * @param moduleConfig
     * @return
     */
    private Properties getProperties(ModuleConfig moduleConfig) {
        Properties properties = toProperties(moduleConfig.getProperties());
        properties.setProperty(MODULE_PROPERTY_NAME, moduleConfig.getName());
        properties.setProperty(MODULE_PROPERTY_VERSION, moduleConfig.getVersion());
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Module Properties: {}", properties);
        }
        return properties;
    }

    /**
     * 查找资源(JAR)中的Spring配置文件
     *
     * @param tempFileJarURLs
     * @param moduleClassLoader
     * @return
     */
    private String[] findSpringConfigs(List<String> tempFileJarURLs, ClassLoader moduleClassLoader,
                                       List<String> exclusionConfigeNameList) {
        PathMatchingResourcePatternResolver pmr = new PathMatchingResourcePatternResolver(moduleClassLoader);
        try {
            Resource[] resources = ImmutableSet.<Resource>builder()
                    .add(pmr.getResources(SPRING_XML_PATTERN[0]))
                    .add(pmr.getResources(SPRING_XML_PATTERN[1]))
                    .build().toArray(new Resource[]{});
            checkNotNull(resources, "resources is null");
            checkArgument(resources.length > 0, "resources length is 0");
            // 因为ClassLoader是树形结构，这里会找到ModuleClassLoader以及其父类中所有符合规范的spring配置文件，所以这里需要过滤，只需要Module Jar中的
            return filterURLsIncludedResources(tempFileJarURLs, resources, exclusionConfigeNameList);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to find spring configs from " + tempFileJarURLs, e);
        }
    }

    /**
     * 过滤查找到的spring配置文件资源，只查找tempFileJarURLs中的spring配置文件
     *
     * @param tempFileJarURLs
     * @param resources
     * @param exclusionConfigeNameList
     * @return
     * @throws IOException
     */
    private String[] filterURLsIncludedResources(List<String> tempFileJarURLs, Resource[] resources,
                                                 List<String> exclusionConfigeNameList) throws IOException {
        List<String> configLocations = Lists.newArrayList();
        for (Resource resource : resources) {
            String configLocation = resource.getURL().toString();
            for (String url : tempFileJarURLs) {
                if (isExclusionConfig(configLocation, exclusionConfigeNameList)) {
                    if (LOGGER.isInfoEnabled()) {
                        LOGGER.info("exclusion url: {}", configLocation);
                    }
                    continue;
                }
                if (configLocation.contains(url)) {
                    configLocations.add(configLocation);
                }
            }
        }
        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("Config locations: {}", configLocations);
        }
        return toArray(configLocations, String.class);
    }

    /**
     * 是否是需要不载入的spring配置
     *
     * @param url
     * @param exclusionConfigeNameList
     * @return
     */
    private boolean isExclusionConfig(String url, List<String> exclusionConfigeNameList) {
        for (String tmp : exclusionConfigeNameList) {
            if (url.contains(tmp)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 去除list中的空白元素，string.startWith("")==true
     *
     * @param moduleConfig
     * @return
     */
    private List<String> getOverridePackages(ModuleConfig moduleConfig) {
        List<String> list = Lists.newArrayList();
        for (String s : moduleConfig.getOverridePackages()) {
            if (!StringUtils.isBlank(s)) {
                list.add(s);
            }
        }
        return list;
    }

    /**
     * Map 转换为Properties
     *
     * @param map
     * @return
     */
    private static Properties toProperties(Map<String, Object> map) {
        Properties properties = new Properties();
        for (Entry<String, Object> each : map.entrySet()) {
            if (each.getKey() == null || each.getValue() == null) {
                if (LOGGER.isWarnEnabled()) {
                    LOGGER.warn("Ignore null properties: {}={}", each.getKey(), each.getValue());
                }
                continue;
            }
            if (StringUtils.isBlank(each.getKey())) {
                continue;
            }
            properties.setProperty(each.getKey(), each.getValue().toString());
        }
        return properties;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private void onLoaded(Module module) {
        if (listenerDispatcher != null) {
            listenerDispatcher.onLoaded(module);
        }
    }

    private void onPreDestroy(Module module) {
        if (listenerDispatcher != null) {
            listenerDispatcher.onPreDestroy(module);
        }
    }

    @Override
    public void setModuleListenerDispatcher(ModuleListenerDispatcher dispatcher) {
        this.listenerDispatcher = dispatcher;
    }

}
