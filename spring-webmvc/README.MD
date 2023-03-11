关于spring源码中com.fasterxml.jackson的使用说明

当前spring项目的源码中原有的com.fasterxml.jackson包可以导致抛出[Caused by: java.lang.ClassNotFoundException: com.fasterxml.jackson.core.util.DefaultPrettyPrinter$Indenter]异常
原因是spring源码中引入的包不全，导致异常

当前源码中使用在gradle中手动引入依赖的方法添加缺少的包
包目录: resources/META-INF/lib
spring-webmvc.gradle中添加本地依赖: implementation fileTree(dir: 'src/main/resources/META-INF/lib', includes: ['*.jar'])
spring-webmvc.gradel中删除原有依赖:
                                    optional("com.fasterxml.jackson.core:jackson-databind")
                                    optional("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
                                    optional("com.fasterxml.jackson.dataformat:jackson-dataformat-smile")
                                    optional("com.fasterxml.jackson.dataformat:jackson-dataformat-cbor")

***本来不打算删除spring原本引入的包，但是可能因为手动引入的本地jar包版本问题导致失效

引入包后AnnotationDrivenBeanDefinitionParser类中的静态代码块会扫描到com.fasterxml.jackson中必要的类
在解析<mvc:annotation-driven></mvc:annotation-driven>标签时会在AnnotationDrivenBeanDefinitionParser::parse方法
中注入RequestMappingHandlerAdapter类的beanDefinition时以propertyValue的方法将必要的messageConverter注入

将会注入的messageConverter包括但不限于：
    1. MappingJackson2XmlHttpMessageConverter
    2. MappingJackson2HttpMessageConverter
在选择响应体类型转换器时，由于默认application/xml消息类型排在application/json前面，因此在请求未指定响应体类型时，默认按照xml的格式写入
如果需要以json格式响应可以在请求头加上 Accept:application/json 参数以指定响应体的写入格式

本来想以删除jackson-dataformat-xml-2.9.0.jar包的方法解决默认application/xml的问题，但是由于是源码，会导致编译不通过
springboot中默认没有引入jackson-dataformat-xml包




***以上所有问题都可以引入fastJson解决
