
# Spring cloud shop


      本系统参考https://github.com/FurionCS/springCloudShop项目进行学习，添加了很多新技术对系统进行了完善

   
## 开发环境
-  MySQL 5.7.17
-  RabbitMQ 3.6.6
-  Java8 with JCE
-  Spring Cloud Camden.SR6
-  redis 3.0
-  mongodb
-  guava

## 项目结构

#### Spring cloud微服务
| 模块名称|     作用|   备注|
| :-------- | --------:| :------: |
| admin|   spring-boot-admin,用于监控| 
|apiGateWay|spring-cloud-zuul,用于做路由，负载均衡|
|common|整个项目的工具类
|config|配置中心|
|hystrixdashboard|hystrix,断融监控|
|order|订单模块|
|proudct|产品模块|
|server|eureka-server,注册中心|
|user|用户模块|
|tcc|tcc事务模块|
|integral|用户积分模块|接受各种事件，进行积分变化
#### 消息中间件
RabbitMQ、Redis
#### 缓存
Redis

# 技术点
## 新增：
   
1、zuul过滤器中进行流量限制（guava RateLimiter基于令牌桶算法限流）

2、redis分布式session，既能保证集群（同一个服务的不同实例）都能获取到用户信息，又实现了单点登录
（不同服务都能通过token查找redis缓存以获取到用户信息，所以所有子系统都能获取用户的登录状态）：
 ```
    具体实现方式为，用户登录后以token作为key，以用户信息作为value，将其存入redis中作为分布式session，
    将token写入cookie中返回前端，后续请求时服务端只需根据cookie中的token到redis中查找用户信息即可；
    此外本项目还利用HandlerMethodArgumentResolver的实现类完成请求接口中User参数的自动注入
```
3、原本使用rabbitMQ的方法服务降级后使用redis实现的异步消息队列（redis list + guava事件驱动编程实现）：
```
    redis实现异步消息队列的方案：
    由于可靠性差，这里没有采用publish/subscribe中天然的异步通信模式，而是采用list结构，具体方案；
    发送方使用lpush发布消息，接收方启动一个线程专门监听消息（使用brpop),收到消息后将其转换为事件，
    使用guava EventBus将事件发送出去，具体的业务方法监听到事件后就会被调用
```

4、redis分布式锁（带守护线程）
```
    redis实现分布式锁的原理：
    首先指定一个key，对其setnx，如果设置成功则表示获取到了锁，如果设置失败则
    表示其他线程占有该锁，处理完业务逻辑后删除该key则表示释放了锁。
    需要注意的点：
    0、获取一个唯一标识（比如uuid），作为该key在redis中的value，后续每一步对key的操作都需判断value是否
    一致，以防止线程在使用锁的时候被其他线程误操作该锁（比如删除）
    1、不管线程获取锁有没有成功都要给该锁设置一个过期时间，以防止获取到锁的服务器还未来的及设置过期时间
    就挂掉了导致锁无法释放。
    2、拿到锁后需另起一个线程给锁续命（隔一段时间检查一次锁是否释放，如果没释放则表示业务逻辑未执行完，
    刷新过期时间），如果获取到锁的服务器挂了，则该守护线程也会一起挂掉，最终锁会过期顺利释放
    3、使用完一定要释放锁
```
5、使用布隆过滤器（guava BloomFilter） + 保存空结果的方法防止缓存穿透
```
    由于BloomFilter中的值只能添加不能删除，所以随着数据库中数据的变化，BloomFilter不能完全保证对无用访问
    进行拦截，因此需要使用保存空结果的方法进行补足，此外还需设置定时任务重新生成BloomFilter防止其失去作用
```
6、使用分布式锁防止缓存击穿
## 原有
7、tcc分布式事务

8、事件驱动编程（guava、rabbitMQ等多种实现方式）

9、jwt+spring security 实现权限校验，在zuul filter中统一鉴权

10、其他spring cloud提供的通用功能

## 启动方式
为了保证开箱即用，我们将config 的需要加载的配置文件放入码云，https://gitee.com/wupeigit/SpringcloudConfig
fork一份到自己的码云。
1：先准备环境，启动mysql,rabbitmq,redis,mongodb,准备带有jce的java环境（加密需要用到），本地系统host加上
```
127.0.0.1 eureka1
127.0.0.1 eureka2
```
2：启动两个server,peer1,peer2
3：我们采用非对称加密，需要本地生成证书，参考http://blog.csdn.net/u014792352/article/details/73163714
获得证书后复制到classpath路径下，在application.yml中配置,然后启动，调用加密接口，将获得密文拷贝到你码云对应的需要替换的地方。
```
encrypt:
  key-store:
    location: server.jks
    password: letmein
    alias: mytestkey
    secret: changeme
```
4：启动config模块=》启动order模块=》产品模块=》用户模块=》tcc模块=》积分模块=》剩余监测模块

## Try Confirm Cancel补偿模式

本实例遵循的是Atomikos公司对微服务的分布式事务所提出的[RESTful TCC](https://www.atomikos.com/Blog/TransactionManagementAPIForRESTTCC)解决方案

RESTful TCC模式分3个阶段执行

1. Trying阶段主要针对业务系统检测及作出预留资源请求, 若预留资源成功, 则返回确认资源的链接与过期时间
2. Confirm阶段主要是对业务系统的预留资源作出确认, 要求TCC服务的提供方要对确认预留资源的接口实现幂等性, 若Confirm成功则返回204, 资源超时则证明已经被回收且返回404
3. Cancel阶段主要是在业务执行错误或者预留资源超时后执行的资源释放操作, Cancel接口是一个可选操作, 因为要求TCC服务的提供方实现自动回收的功能, 所以即便是不认为进行Cancel, 系统也会自动回收资源


## jwt+spring security 实现权限
**1：了解什么是jwt(JSON Web Token)，jwt如何保证安全**
- [使用jwtweb应用间安全传递信息](http://mp.weixin.qq.com/s/bQA4QDpVEP6yTp85MwPGvw)
- [jwt设计单点登录系统](http://mp.weixin.qq.com/s/Gcwc-tgnXzcZuX4rcwL7sQ)


**2：如何使用JWT？**

在身份鉴定的实现中，传统方法是在服务端存储一个session，给客户端返回一个cookie，而使用JWT之后，当用户使用它的认证信息登陆系统之后，会返回给用户一个JWT，用户只需要本地保存该token（通常使用local storage，也可以使用cookie）即可。
<!--more-->
因为用户的状态在服务端的内存中是不存储的，所以这是一种 无状态 的认证机制。服务端的保护路由将会检查请求头 Authorization 中的JWT信息，如果合法，则允许用户的行为。由于JWT是自包含的，因此减少了需要查询数据库的需要。



### 事件驱动编程
    事件驱动通过异步的消息来同步状态，触发某个事件后通过发布消息告知其他服务，消息到达一个服务后可能会引起另外的消息，事件会扩散开
    比如用户登录后，发布一个登录事件，积分服务监听到事件后进行插入积分详情记录，发布详情改变事件，修改用户总积分

### apiGateWay模块
该模块主要是用于路由，授权，鉴权


