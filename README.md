# JVM-SANDBOX模块编写EXAMPLE

> 利用JVM-SANDBOX窥探JVM应用的SOCKET数据

## 开始编写一个MODULE

JVM-SANDBOX是一个强大的AOP框架，既然是强大的，那我们就得用来做一些有别于其他框架的事情。究竟写一个什么作为入门例子比较好呢？既然要特别，那我们就来写一个观察JVM应用的SOCKET通讯的例子吧！

## JDK8(Hotspot)的SOCKET类分析

我们以JDK8(Hotsport)的Socket类为例，所有的SOCKET字节流必定流经过`java.net.SocketInputStream`和`java.net.SocketOutputStream`，所以我们可以在这两个类上做文章，以此来达到目的。

![](https://github.com/alibaba/jvm-sandbox/wiki/img/FOR-EXAMPLE-001-SOCKET.png)

## 开始搭建工程

从`1.0.14`开始，JVM-SANDBOX释出了`sandbox-module-starter`模块，方便大家更快速的开发模块。

```xml
<parent>
	<groupId>com.alibaba.jvm.sandbox</groupId>
	<artifactId>sandbox-module-starter</artifactId>
	<version>1.0.14</version>
</parent>
```

### 构建POM

- 首先我们创建一个maven工程：`sandbox-module-example`，这里装的是本次我们的例子

  ```shell
  mvn archetype:generate -DgroupId=com.alibaba.jvm.sandbox.module.exampe -DartifactId=sandbox-module-example -DarchetypeArtifactId=maven-archetype-quickstart -DinteractiveMode=false
  ```

- 修改pom.xml

  ```xml
  <project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">

    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.alibaba.jvm.sandbox</groupId>
        <artifactId>sandbox-module-starter</artifactId>
        <version>1.0.14</version>
    </parent>

    <groupId>com.alibaba.jvm.sandbox.module.exampe</groupId>
    <artifactId>sandbox-module-example</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <packaging>jar</packaging>
    <name>sandbox-module-example</name>

    <dependencies>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>3.4</version>
        </dependency>
    </dependencies>

  </project>
  ```

  至此，一个JVM-SANDBOX模块的工程框架就搭建起来了。

### 第一个MODULE

JVM-SANDBOX的模块要求符合SPI规范，即

1. **必须实现`com.alibaba.jvm.sandbox.api.Module`接口**
1. **必须拥有无参构造函数**
1. **必须配置`META-INF/services/`**   
   > 实战中，大家都被Spring折腾习惯了，所以很多人其实并不熟悉JSR的SPI规范。导致很多卡在入门的第一步。
 
   > 从`1.0.14`版本开始，你将不需要配置`META-INF/services/`了，只需要给你的module加上注解即可
     
   > ```java
   > @MetaInfServices(Module.class)
   > ```
     
我们开始编写一个模块类，既然是一个统计SOCKET流量的例子，那我们就起一个风骚的名字，叫："SOCKET守望者"，SocketWatchman，简称：ExpSocketWm

```java

package com.alibaba.jvm.sandbox.module.exampe.tcpwm;

/**
 * JVM-SANDBOX练手模块：
 * Socket守望者
 */
@MetaInfServices(Module.class)
@Information(id = "ExpSocketWm", isActiveOnLoad = false, author = "oldmanpushcart@gmail.com", version = "0.0.5")
public class SocketWatchmanModule implements Module, ModuleLifecycle, IoPipe, Runnable {

    @Override
    public void loadCompleted() {

    }
    
}
```

### MODULE的生命周期

- 一个MODULE要发挥作用，必定需要经过三个步骤：

  1. 模块JAR包加载到容器，并完成类初始化和依赖注入工作
  1. 对观察类进行插桩操作（如有）
  1. 激活模块，使得模块能接受Event（如有）

- 同样的，一个MODULE需要被卸载时，反向执行对应的步骤

  1. 冻结模块，模块将无法接收到观察类的Event
  1. 对观察类进行反向插桩，消除本模块对类的影响
  1. 模块类从容器中移除，如若当前模块是JAR文件的最后一个模块，则将整个ModuleClassLoader进行销毁

加载和卸载的生命周期被定义在`com.alibaba.jvm.sandbox.api.ModuleLifecycle`中

#### 模块生命周期

  ![](https://github.com/alibaba/jvm-sandbox/wiki/img/MODULE_LIFECYCLE-001.png)

#### 模块加载核心流程

  ![](https://github.com/alibaba/jvm-sandbox/wiki/img/MODULE_LIFECYCLE-002.png)

我们的SOCKET守望者因为观察的是底层SOCKET流量，所以性能不得不考虑进来。理论上性能最好的是直接使用EventListener，但这里只是一个演示，重点是功能的表达，所以采用了AdviceListener性能稍差的监听器。

同时，流量观察只有在有需要的时候才会触发，所以做了延时激活功能。这样在不观察SOCKET流量时将不会有Advice产生，进一步降低性能开销的影响。

### 代码织入

例子中采用`EventWatchBuilder`来完成本次监听SOCKET的演示，根据上边的代码分析，我们只需要监听

- java.net.SocketInputStream的int:read(byte[],int,int,int);
- java.net.SocketOutputStream的void:socketWrite(byte[],int,int);

这两个方法即可

```java
@Override
public void watching(final IoPipe ioPipe) {

    new EventWatchBuilder(moduleEventWatcher)
            .onClass("java.net.SocketInputStream").includeBootstrap()
            /**/.onBehavior("read").withParameterTypes(byte[].class, int.class, int.class, int.class)
            .onClass("java.net.SocketOutputStream").includeBootstrap()
            /**/.onBehavior("socketWrite").withParameterTypes(byte[].class, int.class, int.class)
            .onWatch(new AdviceListener() {

                final String MARK_R = "MARK_R";
                final String MARK_W = "MARK_W";

                @Override
                protected void before(Advice advice) {
                    final String behaviorName = advice.getBehavior().getName();
                    if ("read".equals(behaviorName)) {
                        advice.mark(MARK_R);
                    } else if ("socketWrite".equals(behaviorName)) {
                        advice.mark(MARK_W);
                    }
                }

                @Override
                protected void afterReturning(Advice advice) {
                    if (advice.hasMark(MARK_R)) {
                        ioPipe.read(
                                (byte[]) advice.getParameterArray()[0],
                                (Integer) advice.getParameterArray()[1],
                                (Integer) advice.getReturnObj()
                        );
                    } else if (advice.hasMark(MARK_W)) {
                        ioPipe.write(
                                (byte[]) advice.getParameterArray()[0],
                                (Integer) advice.getParameterArray()[1],
                                (Integer) advice.getParameterArray()[2]
                        );
                    }
                }

            });
}
```

#### watching()方法代码解读

`new EventWatchBuilder(moduleEventWatcher)`构造一个事件观察者的构造器，通过Builder我们可以方便的构造出我们的观察者。

EventWatchBuilder类有2类核心的方法

- **onXXX**

  on开头的方法表示构造进入一个新的内容，比如
  
  - **onClass()：**

     表示接下来需要对class进行筛选，在SocketWm例子中我们指定了类名作为筛选条件。
    
     因为`java.net.SocketInputStream`在JDK中，由BootstrapClassLoader负责加载，默认情况下是不会检索这个ClassLoader所加载的类。所以必须带上`includeBootstrap()`明确下类的检索范围。
    
  - **onBehavior()：**

     表示需要对上一环节onClass()所匹配的类进行方法级的筛选。在JDK中，是严格区分构造方法和普通方法的，但实际使用上，我们可以把他们两个都抽象为类的行为（Behavior）。
     
     其中构造方法的方法名为`<init>`，普通方法的方法名保持不变。

- **withXXX**

  with开头的表示对当前on所匹配的内容进行筛选，在SocketWm例子中，我们用`withParameterTypes(...)`对匹配的行为做进一步筛选
  
  ```java
  .onBehavior("socketWrite")
  .withParameterTypes(byte[].class, int.class, int.class)
  ```


#### 其他辅助类解读  

- IoPipe是我们定义出来感知数据流的一个接口，通过这个接口，你除了能感知流量吞吐之外，还能有机会窥探到SOCKET流经的数据

  ```java
  /**
   * IO管道
   */
  public interface IoPipe {
  
      /**
       * 读
       *
       * @param buf 数据缓冲
       * @param off 偏移量
       * @param len 读出长度
       */
      void read(byte buf[], int off, int len);

      /**
       * 写
       *
       * @param buf 数据缓冲
       * @param off 偏移量
       * @param len 写入长度
       */
      void write(byte buf[], int off, int len);

  }
  ```

- SocketWatcher是对不同厂商、不同版本JDK实现隔离的抽象接口。因为SocketInputStream和SocketOutputStream都不是public的类，说不准哪天JDK代码重构中就有可能干掉，所以我们必须要考虑到兼容不同JDK版本的需要。

  ```java
  /**
   * TCP观察者
   */
  public interface SocketWatcher {
  
      /**
       * 观察IO吞吐量
       *
       * @param ioThroughput IO吞吐
       */
      void watching(IoPipe ioThroughput);

      class Factory {
  
          static SocketWatcher make(final ModuleEventWatcher moduleEventWatcher) {
              // 各种平台适配...
              return new SocketWatcherImplHotspotJDK8(moduleEventWatcher);
          }

      }

  }
  ```

  因为这个例子只是一个演示，所以我选择了JDK8来进行对标。这两个类从JDK7开始就没有什么变化，所以虽然用的JDK8对标，但可以适用在JDK7+~JDK8的版本。JDK6是肯定不行，有兴趣的可以自己去实现。

## 部署运行

### 编译打包

这个例子中我们是用MAVEN来组织我们的工程的，所以打包环节我们也继续使用maven相关命令。

由于pom继承了`sandbox-module-starter`，模块的很多插件要求都在这个pom中帮你打点好了。你只需要执行：

```shell
mvn clean package
```

接下来就是收获的季节

```shell
duxiaokundeMacBook-Pro:target vlinux$ ls -lrt
total 1056
drwxr-xr-x   4 vlinux  staff     128  2 27 22:50 classes
drwxr-xr-x   3 vlinux  staff      96  2 27 22:50 maven-status
drwxr-xr-x   3 vlinux  staff      96  2 27 22:50 generated-sources
drwxr-xr-x   3 vlinux  staff      96  2 27 22:50 maven-archiver
-rw-r--r--   1 vlinux  staff   11868  2 27 22:50 sandbox-module-example-1.0.0-SNAPSHOT.jar
drwxr-xr-x   4 vlinux  staff     128  2 27 22:50 javadoc-bundle-options
drwxr-xr-x  16 vlinux  staff     512  2 27 22:50 apidocs
-rw-r--r--   1 vlinux  staff   68238  2 27 22:50 sandbox-module-example-1.0.0-SNAPSHOT-javadoc.jar
-rw-r--r--   1 vlinux  staff    7970  2 27 22:50 sandbox-module-example-1.0.0-SNAPSHOT-sources.jar
drwxr-xr-x   2 vlinux  staff      64  2 27 22:50 archive-tmp
-rw-r--r--   1 vlinux  staff  447428  2 27 22:50 sandbox-module-example-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

`sandbox-module-example-1.0.0-SNAPSHOT-jar-with-dependencies.jar`就是我们最终输出的模块JAR包。

### 运行调试

#### 部署模块包

把`sandbox-module-example-1.0.0-SNAPSHOT-jar-with-dependencies.jar`文件放在`${HOME}/.sandbox-module/`目录下

#### 下载并安装JVM-SANDBOX

下载当前[稳定的JVM-SANDBOX容器版本](http://ompc.oss-cn-hangzhou.aliyuncs.com/jvm-sandbox/release/sandbox-stable-bin.zip)，解压并安装

```shell
unzip sandbox-stable-bin.zip
cd sandbox
./install-local.sh -p ~/opt
```

这样，sandbox就会安装到 `${HOME}/opt/sandbox`文件夹中

#### 试运行

找一个有流量的JVM应用，我在本地启了一个WEB应用，把JVM-SANDBOX挂上。

```shell
duxiaokundeMacBook-Pro:bin vlinux$ ./sandbox.sh -p 10491 -l
debug-ralph         	ACTIVE  	LOADED  	0    	0    	0.0.1          	luanjia@taobao.com
debug-exception-logger	ACTIVE  	LOADED  	1    	5    	0.0.1          	luanjia@taobao.com
debug-http-logger   	ACTIVE  	LOADED  	2    	4    	0.0.1          	luanjia@taobao.com
debug-trace         	ACTIVE  	LOADED  	0    	0    	0.0.1          	luanjia@taobao.com
debug-jdbc-logger   	ACTIVE  	LOADED  	6    	59   	0.0.1          	luanjia@taobao.com
module-mgr          	ACTIVE  	LOADED  	0    	0    	0.0.1          	luanjia@taobao.com
debug-watch         	ACTIVE  	LOADED  	0    	0    	0.0.1          	luanjia@taobao.com
debug-spring-logger 	ACTIVE  	LOADED  	3    	10   	0.0.1          	luanjia@taobao.com
control             	ACTIVE  	LOADED  	0    	0    	0.0.1          	luanjia@taobao.com
ExpSocketWm         	FROZEN  	LOADED  	2    	2    	0.0.5          	oldmanpushcart@gmail.com
info                	ACTIVE  	LOADED  	0    	0    	0.0.3          	luanjia@taobao.com
total=11
```

可以看到ExpSocketWm已经加载进来，但是`FROZEN`状态，增强了2个类2个方法，嗯，符合我们的预期。

因为ExpSocketWm是观察时才激活，所以这里我们需要开始观察流量，类的状态才会变更为`ACTIVE`

```shell
duxiaokundeMacBook-Pro:bin vlinux$ ./sandbox.sh -p 10491 -d 'ExpSocketWm/show'
SocketWatchman is working.
Press CTRL_C abort it!
 READ : RATE=0.00(kb)/sec ; TOTAL=0.00(kb)
WRITE : RATE=0.00(kb)/sec ; TOTAL=0.00(kb)
statistics in 5(sec).

 READ : RATE=38.53(kb)/sec ; TOTAL=188.14(kb)
WRITE : RATE=1.49(kb)/sec ; TOTAL=7.26(kb)
statistics in 5(sec).

```

NICE，正常工作。

## 其他自带例子

ExpSocketWm是我自己学习JVM-SANDBOX所联系的一个例子，其实分发包中自带了不少的Example，也都是学习的好资料

|例子|例子说明|
|---|:---|
|[DebugWatchModule.java](https://github.com/alibaba/jvm-sandbox/blob/master/sandbox-debug-module/src/main/java/com/alibaba/jvm/sandbox/module/debug/DebugWatchModule.java)|模仿GREYS的watch命令|
|[DebugTraceModule.java](https://github.com/alibaba/jvm-sandbox/blob/master/sandbox-debug-module/src/main/java/com/alibaba/jvm/sandbox/module/debug/DebugTraceModule.java)|模仿GREYES的trace命令|
|[DebugRalphModule.java](https://github.com/alibaba/jvm-sandbox/blob/master/sandbox-debug-module/src/main/java/com/alibaba/jvm/sandbox/module/debug/DebugRalphModule.java)|无敌破坏王，故障注入（延时、熔断、并发限流、TPS限流）|
|[ExceptionLoggerModule.java](https://github.com/alibaba/jvm-sandbox/blob/master/sandbox-debug-module/src/main/java/com/alibaba/jvm/sandbox/module/debug/ExceptionLoggerModule.java)|记录下你的应用都发生了哪些异常<br/>$HOME/logs/sandbox/debug/exception-monitor.log|
|[HttpHttpAccessLoggerModule.java](https://github.com/alibaba/jvm-sandbox/blob/master/sandbox-debug-module/src/main/java/com/alibaba/jvm/sandbox/module/debug/HttpHttpAccessLoggerModule.java)|记录下你的应用的HTTP服务请求<br/>$HOME/logs/sandbox/debug/servlet-monitor.log|
|[JdbcLoggerModule.java](https://github.com/alibaba/jvm-sandbox/blob/master/sandbox-debug-module/src/main/java/com/alibaba/jvm/sandbox/module/debug/JdbcLoggerModule.java)|记录下你的应用数据库访问请求<br/>$HOME/logs/sandbox/debug/jdbc-monitor.log|
|[SpringLoggerModule.java](https://github.com/alibaba/jvm-sandbox/blob/master/sandbox-debug-module/src/main/java/com/alibaba/jvm/sandbox/module/debug/SpringLoggerModule.java)|记录下Spring相关请求<br/>$HOME/logs/sandbox/debug/spring-monitor.log|

## 写在后边

例子已经上传到我的GITHUB：[oldmanpushcart/sandbox-module-example](https://github.com/oldmanpushcart/sandbox-module-example)
