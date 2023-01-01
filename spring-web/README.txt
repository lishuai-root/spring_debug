org.springframework.web.multipart.commons 该包于2021/09/17日被spring删除，
来自spring的提交注释("Upgrades many dependency declarations; removes old EJB 2.x support and outdated Servlet-based integrations (Commons FileUpload, FreeMarker JSP support, Tiles).")
该项目中的包是从spring-framework-5.3.25-SNAPSHOT版本中复制过来的

由于spring6.0+版本删除了对javax的依赖，改使用jakarta包，因此在复制spring-framework-5.3.25-SNAPSHOT的包后需要在spring-web.gradle中添加一下依赖

implementation('commons-fileupload:commons-fileupload:1.4')
implementation fileTree(dir: 'src/main/resources/META-INF/lib', includes: ['*.jar'])
optional("javax.servlet:javax.servlet-api") // Servlet 4 for mapping type
optional("javax.servlet.jsp:javax.servlet.jsp-api")
optional("javax.el:javax.el-api")
optional("javax.faces:javax.faces-api")
optional("javax.json.bind:javax.json.bind-api")
optional("javax.mail:javax.mail-api")
optional("javax.validation:validation-api")
optional("javax.xml.bind:jaxb-api")
optional("javax.xml.ws:jaxws-api")
optional("org.glassfish.main:javax.jws")


由于javax的依赖下载报错，此处使用下面的方式使用gradle引入了本地jar包
1.创建src/main/resources/META-INF/lib目录，并将需要的jar包复制到该目录下
2.在spring-web.gradle中添加一下依赖

/**
 * -------------------------------------------------------------------------
 *	添加org.springframework.web.multipart.commons后需要添加一下依赖
 * -------------------------------------------------------------------------
 */
implementation('commons-fileupload:commons-fileupload:1.4')
//	以本地jar包的方式引入依赖
implementation fileTree(dir: 'src/main/resources/META-INF/lib', includes: ['*.jar'])

---------------------------目录中包含一下jar包--------------------------------
---------------------------------start--------------------------------------
el-api.jar
javax.faces-api-2.2.jar
javax.json.bind-api-1.0.jar
javax.jws-4.0-b33.jar
javax.mail-api-1.6.2.jar
jaxb-api-2.3.1.jar
jaxws-api-2.3.1.jar
jsp-api.jar
servlet-api.jar
validation-api-2.0.1.Final.jar
----------------------------------end---------------------------------------