# 简介

[![Build Status](https://travis-ci.org/sofastack/sofa-jarslink.svg?branch=master)](https://travis-ci.org/sofastack/sofa-jarslink)
[![Coverage Status](https://coveralls.io/repos/github/sofastack/sofa-jarslink/badge.svg?branch=master)](https://coveralls.io/github/sofastack/sofa-jarslink)
![license](https://img.shields.io/badge/license-Apache--2.0-green.svg)
![maven](https://img.shields.io/nexus/s/https/oss.sonatype.org/com.alipay.sofa/sofa-jarslink.svg)


Jarslink 2.0 是 [SOFABoot](https://github.com/sofastack/sofa-boot) 官方基于 [SOFAArk](https://github.com/sofastack/sofa-ark) 开发的功能插件，负责管理多应用在 SOFAArk 容器之上的合并部署，具备如下特性：

+ 支持运行时动态安装和卸载应用。

+ 支持运行时应用热替换能力，保证服务的连续性。

+ 跨应用内部通信，支持应用发布引用 JVM 服务，跨应用既可以使用 RPC 框架，也可以走内部 JVM 服务进行通信。

+ 支持应用健康检查。


# 背景
在蚂蚁金服内部，在同一个 JVM 之上部署多个应用，是一件常见的事情。这样带来的主要优势如下：

+ 无关应用合并部署：有些应用在独立部署时，相互之间没有服务依赖，而且这些应用承担业务体量都偏小，单独启动 Java 虚拟机比较浪费资源， 将这些应用合并部署，能够节省资源。

+ 相关应用合并部署：有些应用之间存在服务依赖，独立部署时，各应用之间使用 RPC 调用，虽然使用了分布式架构，稳定性高，但依然存在网络抖动导致的延时性问题。这些应用合并部署，RPC 调用优先转为 JVM 内部调用，缩减调用开销。

不仅应用间存在合并部署，近端包也有同样的诉求。

近端包是提供一系列公共服务的三方组件，一般由应用作为依赖引入，这种开发模式容易导致两个问题：

+ 近端包引入的三方依赖和应用本身的依赖产生冲突，期望能做到隔离部署。

+ 近端包由应用作为依赖引入，因此近端包的任何升级改造都需要应用配合升级。但是作为一个公共的功能组件，近端包通常会被很多业务方应用依赖，此时推动业务方改造工作量巨大，因此期望能做到近端包的动态升级。

除了合并部署，蚂蚁金服很多业务场景需要模块的热部署，即在应用运行时，需要动态替换某特定模块而不影响其他模块的正常运行。

Jarslink2.0  正是为了解决诸如此类的问题，它是基于 SOFAArk 开发的 Ark Plugin，用于管理多应用合并部署。在了解 Jarslink2.0 之前，你需要提前了解 SOFAArk 框架。关于 SOFAArk 可以访问[链接](http://www.sofastack.tech/sofa-boot/docs/sofa-ark-readme)获取更多详细信息。

# 原理
Jarslink2.0 是一款基于 SOFAArk 开发的 [Ark Plugin](http://www.sofastack.tech/sofa-boot/docs/sofa-ark-ark-plugin) 。假设你已经对 SOFAArk 有一定的了解，很容易知道，应用被打包成 [Ark Biz](http://www.sofastack.tech/sofa-boot/docs/sofa-ark-ark-biz) 的形式运行在 SOFAArk 容器之上。SOFABoot 或者 Spring Boot 应用，甚至普通的模块都可以借助 SOFAArk 插件打包成一个标准的 Ark Biz 包。

Jarslink2.0 支持多个 Ark Biz 运行在 SOFAArk 容器之上，从而做到多应用的合并部署。应用可以通过注解的形式快速发布服务或者引用其他应用发布的服务，达到相互通信的目的。下图是运行时多应用合并部署结构图：

![undefined](./resource/jarslink-runtime.png) 

从图中可以看到，使用 Jarslink2.0 通常需要引入两个 Ark Plugin, 下面介绍这两个 Ark Plugin 的作用。

+ Jarslink: Jarslink2.0 核心代码，支持动态接收命令，如安装、卸载、切换等等，用于管理 Ark Biz 的生命周期。如果需要运行时动态部署应用，需要添加如下依赖：
```xml
<dependency>
    <groupId>com.alipay.sofa</groupId>
    <artifactId>sofa-jarslink-ark-starter</artifactId>
    <classifier>ark-plugin</classifier>
</dependency>
```
+ SOFARuntime: SOFARuntime 是 SOFABoot 提供的功能模块，用于实现跨应用的服务调用。如果需要使用跨应用调用功能，需要添加如下依赖：
```xml
<dependency>
    <groupId>com.alipay.sofa</groupId>
    <artifactId>runtime-sofa-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>com.alipay.sofa</groupId>
    <artifactId>runtime-sofa-boot-starter</artifactId>
    <classifier>ark-plugin</classifier>
</dependency>
```
# 快速开始
* [如何创建 SOFABoot 应用](http://www.sofastack.tech/sofa-boot/docs/sofa-jarslink-jarslink-app-demo)
* [如何使用 Jarslink 多应用动态部署](http://www.sofastack.tech/sofa-boot/docs/sofa-jarslink-jarslink-deploy-demo)
* [如何使用跨应用通信](http://www.sofastack.tech/sofa-boot/docs/sofa-jarslink-jarslink-invocation-demo)
* [如何集成 SOFABoot 健康检查](http://www.sofastack.tech/sofa-boot/docs/sofa-jarslink-jarslink-health-demo)

# 贡献
+ [代码贡献](./CONTRIBUTING.md): Jarslink 开发参与说明书

# 文档
+ [Jarslink 用户手册(中文)](http://www.sofastack.tech/sofa-boot/docs/sofa-jarslink-jarslink-readme): Jarslink 用户手册及功能特性说明
