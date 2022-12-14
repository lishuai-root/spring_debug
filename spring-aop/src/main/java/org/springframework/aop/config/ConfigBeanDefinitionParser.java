/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop.config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.springframework.aop.aspectj.*;
import org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.springframework.aop.support.DefaultBeanFactoryPointcutAdvisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanNameReference;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.parsing.ParseState;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;

/**
 * {@link BeanDefinitionParser} for the {@code <aop:config>} tag.
 *
 * @author Rob Harrop
 * @author Juergen Hoeller
 * @author Adrian Colyer
 * @author Mark Fisher
 * @author Ramnivas Laddad
 * @since 2.0
 */
class ConfigBeanDefinitionParser implements BeanDefinitionParser {

	private static final String ASPECT = "aspect";
	private static final String EXPRESSION = "expression";
	private static final String ID = "id";
	private static final String POINTCUT = "pointcut";
	private static final String ADVICE_BEAN_NAME = "adviceBeanName";
	private static final String ADVISOR = "advisor";
	private static final String ADVICE_REF = "advice-ref";
	private static final String POINTCUT_REF = "pointcut-ref";
	private static final String REF = "ref";
	private static final String BEFORE = "before";
	private static final String DECLARE_PARENTS = "declare-parents";
	private static final String TYPE_PATTERN = "types-matching";
	private static final String DEFAULT_IMPL = "default-impl";
	private static final String DELEGATE_REF = "delegate-ref";
	private static final String IMPLEMENT_INTERFACE = "implement-interface";
	private static final String AFTER = "after";
	private static final String AFTER_RETURNING_ELEMENT = "after-returning";
	private static final String AFTER_THROWING_ELEMENT = "after-throwing";
	private static final String AROUND = "around";
	private static final String RETURNING = "returning";
	private static final String RETURNING_PROPERTY = "returningName";
	private static final String THROWING = "throwing";
	private static final String THROWING_PROPERTY = "throwingName";
	private static final String ARG_NAMES = "arg-names";
	private static final String ARG_NAMES_PROPERTY = "argumentNames";
	private static final String ASPECT_NAME_PROPERTY = "aspectName";
	private static final String DECLARATION_ORDER_PROPERTY = "declarationOrder";
	private static final String ORDER_PROPERTY = "order";
	private static final int METHOD_INDEX = 0;
	private static final int POINTCUT_INDEX = 1;
	private static final int ASPECT_INSTANCE_FACTORY_INDEX = 2;

	private ParseState parseState = new ParseState();


	@Override
	@Nullable
	public BeanDefinition parse(Element element, ParserContext parserContext) {
		CompositeComponentDefinition compositeDef =
				new CompositeComponentDefinition(element.getTagName(), parserContext.extractSource(element));
		parserContext.pushContainingComponent(compositeDef);

		/**
		 * 注册解析<aop:config/>需要用到的org.springframework.aop.config.internalAutoProxyCreator名称的beanDefinition
		 * 类型为{@link AspectJAwareAdvisorAutoProxyCreator}
		 */
		configureAutoProxyCreator(parserContext, element);

		List<Element> childElts = DomUtils.getChildElements(element);
		for (Element elt: childElts) {
			String localName = parserContext.getDelegate().getLocalName(elt);
			if (POINTCUT.equals(localName)) {
				parsePointcut(elt, parserContext);
			}
			else if (ADVISOR.equals(localName)) {
				parseAdvisor(elt, parserContext);
			}
			else if (ASPECT.equals(localName)) {
				parseAspect(elt, parserContext);
			}
		}

		parserContext.popAndRegisterContainingComponent();
		return null;
	}

	/**
	 * Configures the auto proxy creator needed to support the {@link BeanDefinition BeanDefinitions}
	 * created by the '{@code <aop:config/>}' tag. Will force class proxying if the
	 * '{@code proxy-target-class}' attribute is set to '{@code true}'.
	 *
	 * 配置支持由“{@code <aop:config>}”标签创建的 {@link BeanDefinition BeanDefinitions} 所需的自动代理创建者。
	 * 如果“{@code proxy-target-class}”属性设置为“{@code true}”，将强制进行类代理。
	 *
	 * 1.给bean工厂中注入org.springframework.aop.config.internalAutoProxyCreator名称的beanDefinition，
	 * 类型为{@link AspectJAwareAdvisorAutoProxyCreator}
	 * 2.标记proxy-target-class和expose-proxy的属性值
	 * 3.把org.springframework.aop.config.internalAutoProxyCreator名称的beanDefinition注册为组件
	 *
	 * 如果proxy-target-class的属性值为true，则使用cglib代理，否则使用jdk代理
	 *
	 * @see AopNamespaceUtils
	 */
	private void configureAutoProxyCreator(ParserContext parserContext, Element element) {
		AopNamespaceUtils.registerAspectJAutoProxyCreatorIfNecessary(parserContext, element);
	}

	/**
	 * Parses the supplied {@code <advisor>} element and registers the resulting
	 * {@link org.springframework.aop.Advisor} and any resulting {@link org.springframework.aop.Pointcut}
	 * with the supplied {@link BeanDefinitionRegistry}.
	 */
	private void parseAdvisor(Element advisorElement, ParserContext parserContext) {
		AbstractBeanDefinition advisorDef = createAdvisorBeanDefinition(advisorElement, parserContext);
		String id = advisorElement.getAttribute(ID);

		try {
			this.parseState.push(new AdvisorEntry(id));
			String advisorBeanName = id;
			if (StringUtils.hasText(advisorBeanName)) {
				parserContext.getRegistry().registerBeanDefinition(advisorBeanName, advisorDef);
			}
			else {
				advisorBeanName = parserContext.getReaderContext().registerWithGeneratedName(advisorDef);
			}

			Object pointcut = parsePointcutProperty(advisorElement, parserContext);
			if (pointcut instanceof BeanDefinition) {
				advisorDef.getPropertyValues().add(POINTCUT, pointcut);
				parserContext.registerComponent(
						new AdvisorComponentDefinition(advisorBeanName, advisorDef, (BeanDefinition) pointcut));
			}
			else if (pointcut instanceof String) {
				advisorDef.getPropertyValues().add(POINTCUT, new RuntimeBeanReference((String) pointcut));
				parserContext.registerComponent(
						new AdvisorComponentDefinition(advisorBeanName, advisorDef));
			}
		}
		finally {
			this.parseState.pop();
		}
	}

	/**
	 * Create a {@link RootBeanDefinition} for the advisor described in the supplied. Does <strong>not</strong>
	 * parse any associated '{@code pointcut}' or '{@code pointcut-ref}' attributes.
	 */
	private AbstractBeanDefinition createAdvisorBeanDefinition(Element advisorElement, ParserContext parserContext) {
		RootBeanDefinition advisorDefinition = new RootBeanDefinition(DefaultBeanFactoryPointcutAdvisor.class);
		advisorDefinition.setSource(parserContext.extractSource(advisorElement));

		String adviceRef = advisorElement.getAttribute(ADVICE_REF);
		if (!StringUtils.hasText(adviceRef)) {
			parserContext.getReaderContext().error(
					"'advice-ref' attribute contains empty value.", advisorElement, this.parseState.snapshot());
		}
		else {
			advisorDefinition.getPropertyValues().add(
					ADVICE_BEAN_NAME, new RuntimeBeanNameReference(adviceRef));
		}

		if (advisorElement.hasAttribute(ORDER_PROPERTY)) {
			advisorDefinition.getPropertyValues().add(
					ORDER_PROPERTY, advisorElement.getAttribute(ORDER_PROPERTY));
		}

		return advisorDefinition;
	}

	/**
	 * 解析<aop:aspect/>标签
	 *
	 * @param aspectElement
	 * @param parserContext
	 */
	private void parseAspect(Element aspectElement, ParserContext parserContext) {
		String aspectId = aspectElement.getAttribute(ID);
		String aspectName = aspectElement.getAttribute(REF);

		try {
			this.parseState.push(new AspectEntry(aspectId, aspectName));
			// 保存切点对应的beanDefinition
			List<BeanDefinition> beanDefinitions = new ArrayList<>();

			// 保存bean的运行时引用(以名称的方式引用另一个bean的定义)，eg:pointcut-ref="proxyCut"
			List<BeanReference> beanReferences = new ArrayList<>();

			/**
			 * 获取<aop:aspect/>标签下所有的<aop:declare-parents/>标签
			 * 只查找直接子标签，不会递归解析子标签
			 */
			List<Element> declareParents = DomUtils.getChildElementsByTagName(aspectElement, DECLARE_PARENTS);
			/**
			 * 解析<aop:declare-parents/>标签，并创建beanDefinition以组件的形式注册到bean工厂
			 */
			for (int i = METHOD_INDEX; i < declareParents.size(); i++) {
				Element declareParentsElement = declareParents.get(i);
				beanDefinitions.add(parseDeclareParents(declareParentsElement, parserContext));
			}

			// We have to parse "advice" and all the advice kinds in one loop, to get the
			// ordering semantics right.
			/**
			 * 我们必须在一个循环中解析“advice”和所有的advice种类，以得到正确的排序语义。
			 *
			 * 解析当前切面下<aop:around/>, <aop:before/>, <aop:after/>, <aop:after-returning/>, <aop:after-throwing/>标签
			 * 并使用定义信息创建对应实现类的beanDefinition
			 *
			 * <aop:around/> : {@link AspectJAroundAdvice}
			 * <aop:before/> : {@link AspectJMethodBeforeAdvice}
			 * <aop:after/> : {@link AspectJAfterAdvice}
			 * <aop:after-returning/> : {@link AspectJAfterReturningAdvice}
			 * <aop:after-throwing/> : {@link AspectJAfterThrowingAdvice}
			 * 默认实现类都继承了{@link org.springframework.aop.aspectj.AbstractAspectJAdvice}
			 *
			 * {@link org.aopalliance.aop.Advice}会被封装成{@link org.springframework.aop.Advisor}
			 *
			 * {@link AspectJPointcutAdvisor#advice}
			 *
			 */
			NodeList nodeList = aspectElement.getChildNodes();
			boolean adviceFoundAlready = false;
			for (int i = 0; i < nodeList.getLength(); i++) {
				Node node = nodeList.item(i);
				if (isAdviceNode(node, parserContext)) {
					if (!adviceFoundAlready) {
						adviceFoundAlready = true;
						if (!StringUtils.hasText(aspectName)) {
							parserContext.getReaderContext().error(
									"<aspect> tag needs aspect bean reference via 'ref' attribute when declaring advices.",
									aspectElement, this.parseState.snapshot());
							return;
						}
						beanReferences.add(new RuntimeBeanReference(aspectName));
					}
					AbstractBeanDefinition advisorDefinition = parseAdvice(
							aspectName, i, aspectElement, (Element) node, parserContext, beanDefinitions, beanReferences);
					beanDefinitions.add(advisorDefinition);
				}
			}

			/**
			 * 把解析到的切点定义信息封装成{@link AspectComponentDefinition}, 并注入到组件定义中
			 */
			AspectComponentDefinition aspectComponentDefinition = createAspectComponentDefinition(
					aspectElement, aspectId, beanDefinitions, beanReferences, parserContext);
			parserContext.pushContainingComponent(aspectComponentDefinition);

			/**
			 * 解析<aop:pointcut/>标签并创建切点对应的beanDefinition，并注入到组件定义中
			 */
			List<Element> pointcuts = DomUtils.getChildElementsByTagName(aspectElement, POINTCUT);
			for (Element pointcutElement : pointcuts) {
				parsePointcut(pointcutElement, parserContext);
			}

			parserContext.popAndRegisterContainingComponent();
		}
		finally {
			this.parseState.pop();
		}
	}

	/**
	 * 创建一个复合组件定义信息
	 *
	 * @param aspectElement
	 * @param aspectId
	 * @param beanDefs
	 * @param beanRefs
	 * @param parserContext
	 * @return
	 */
	private AspectComponentDefinition createAspectComponentDefinition(
			Element aspectElement, String aspectId, List<BeanDefinition> beanDefs,
			List<BeanReference> beanRefs, ParserContext parserContext) {

		BeanDefinition[] beanDefArray = beanDefs.toArray(new BeanDefinition[0]);
		BeanReference[] beanRefArray = beanRefs.toArray(new BeanReference[0]);
		Object source = parserContext.extractSource(aspectElement);
		return new AspectComponentDefinition(aspectId, beanDefArray, beanRefArray, source);
	}

	/**
	 * Return {@code true} if the supplied node describes an advice type. May be one of:
	 * '{@code before}', '{@code after}', '{@code after-returning}',
	 * '{@code after-throwing}' or '{@code around}'.
	 *
	 * 如果提供的节点描述了通知类型，则返回 {@code true}。
	 * 可能是以下之一：“{@code before}”、“{@code after}”、“{@code after-returning}”、“{@code after-throwing}”或“{@code around}”。
	 *
	 * 判断标签是否一个{@link org.aopalliance.aop.Advice}类型的通知
	 */
	private boolean isAdviceNode(Node aNode, ParserContext parserContext) {
		if (!(aNode instanceof Element)) {
			return false;
		}
		else {
			String name = parserContext.getDelegate().getLocalName(aNode);
			return (BEFORE.equals(name) || AFTER.equals(name) || AFTER_RETURNING_ELEMENT.equals(name) ||
					AFTER_THROWING_ELEMENT.equals(name) || AROUND.equals(name));
		}
	}

	/**
	 * Parse a '{@code declare-parents}' element and register the appropriate
	 * DeclareParentsAdvisor with the BeanDefinitionRegistry encapsulated in the
	 * supplied ParserContext.
	 *
	 * 解析“{@code declare-parents}”元素并使用封装在提供的 ParserContext 中的 BeanDefinitionRegistry 注册适当的 DeclareParentsAdvisor。
	 *
	 * 解析并创建<aop:declare-parents/>标签对应的beanDefinition({@link DeclareParentsAdvisor})
	 */
	private AbstractBeanDefinition parseDeclareParents(Element declareParentsElement, ParserContext parserContext) {
		/**
		 * 为当前<aop:declare-parents/>标签创建{@link DeclareParentsAdvisor}类型的beanDefinition
		 * 并封装成BeanDefinitionBuilder
		 */
		BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(DeclareParentsAdvisor.class);
		/**
		 * 设置{@link DeclareParentsAdvisor}类型实例构造函数参数
		 *
		 * {@link DeclareParentsAdvisor}没有默认构造函数
		 */
		builder.addConstructorArgValue(declareParentsElement.getAttribute(IMPLEMENT_INTERFACE));
		builder.addConstructorArgValue(declareParentsElement.getAttribute(TYPE_PATTERN));

		/**
		 * 为beanDefinition设置拦截器类或拦截器引用
		 */
		String defaultImpl = declareParentsElement.getAttribute(DEFAULT_IMPL);
		String delegateRef = declareParentsElement.getAttribute(DELEGATE_REF);

		if (StringUtils.hasText(defaultImpl) && !StringUtils.hasText(delegateRef)) {
			builder.addConstructorArgValue(defaultImpl);
		}
		else if (StringUtils.hasText(delegateRef) && !StringUtils.hasText(defaultImpl)) {
			builder.addConstructorArgReference(delegateRef);
		}
		else {
			parserContext.getReaderContext().error(
					"Exactly one of the " + DEFAULT_IMPL + " or " + DELEGATE_REF + " attributes must be specified",
					declareParentsElement, this.parseState.snapshot());
		}

		/**
		 * 为{@link DeclareParentsAdvisor}类型的beanDefinition设置元数据，
		 * 使用命名生成器为beanDefinition生成名称，并注入到bean工厂
		 */
		AbstractBeanDefinition definition = builder.getBeanDefinition();
		definition.setSource(parserContext.extractSource(declareParentsElement));
		parserContext.getReaderContext().registerWithGeneratedName(definition);
		return definition;
	}

	/**
	 * Parses one of '{@code before}', '{@code after}', '{@code after-returning}',
	 * '{@code after-throwing}' or '{@code around}' and registers the resulting
	 * BeanDefinition with the supplied BeanDefinitionRegistry.
	 * 解析“{@code before}”、“{@code after}”、“{@code after-returning}”、“{@code after-throwing}”或“{@code around}”之一，
	 * 并注册结果带有提供的 BeanDefinitionRegistry 的 BeanDefinition。
	 *
	 * 解析相关标签，并创建对应实现类的beanDefinition，最终封装成{@link AspectJPointcutAdvisor}类型的beanDefinition，并注入bean工厂
	 *
	 * 1.创建切点需要织入的方法beanDefinition
	 * 2.创建切点实例化工厂
	 * 3.创建对应类型的切点beanDefinition
	 * 4.把切点封装成{@link AspectJPointcutAdvisor}类型的beanDefinition并注入bean工厂
	 *
	 * @return the generated advice RootBeanDefinition
	 */
	private AbstractBeanDefinition parseAdvice(
			String aspectName, int order, Element aspectElement, Element adviceElement, ParserContext parserContext,
			List<BeanDefinition> beanDefinitions, List<BeanReference> beanReferences) {

		try {
			this.parseState.push(new AdviceEntry(parserContext.getDelegate().getLocalName(adviceElement)));

			// create the method factory bean
			/**
			 * 创建方法工厂bean
			 *
			 * 创建切面需要织入的方法定义信息
			 */
			RootBeanDefinition methodDefinition = new RootBeanDefinition(MethodLocatingFactoryBean.class);
			methodDefinition.getPropertyValues().add("targetBeanName", aspectName);
			methodDefinition.getPropertyValues().add("methodName", adviceElement.getAttribute("method"));
			methodDefinition.setSynthetic(true);

			// create instance factory definition
			/**
			 * 创建实例工厂定义
			 *
			 * 生成创建切点对象的实例工厂
			 */
			RootBeanDefinition aspectFactoryDef =
					new RootBeanDefinition(SimpleBeanFactoryAwareAspectInstanceFactory.class);
			aspectFactoryDef.getPropertyValues().add("aspectBeanName", aspectName);
			aspectFactoryDef.setSynthetic(true);

			// register the pointcut
			/**
			 * 注册切入点
			 *
			 * 创建切入点实现类的beanDefinition，{@link org.springframework.aop.aspectj.AbstractAspectJAdvice}的实现类
			 */
			AbstractBeanDefinition adviceDef = createAdviceDefinition(
					adviceElement, parserContext, aspectName, order, methodDefinition, aspectFactoryDef,
					beanDefinitions, beanReferences);

			// configure the advisor
			/**
			 * 配置顾问
			 * 创建{@link AspectJPointcutAdvisor}类型的beanDefinition
			 *
			 * 每一个{@link AbstractAspectJAdvice}都会被封装成一个{@link AspectJPointcutAdvisor}
			 * {@link AspectJPointcutAdvisor}只有一个带有一个参数的构造函数{@link AspectJPointcutAdvisor#AspectJPointcutAdvisor(AbstractAspectJAdvice)}
			 */
			RootBeanDefinition advisorDefinition = new RootBeanDefinition(AspectJPointcutAdvisor.class);

			// 设置切点元数据信息
			advisorDefinition.setSource(parserContext.extractSource(adviceElement));

			/**
			 * 设置{@link AspectJPointcutAdvisor}的构造器参数
			 */
			advisorDefinition.getConstructorArgumentValues().addGenericArgumentValue(adviceDef);

			// 如果必要设置切点执行顺序
			if (aspectElement.hasAttribute(ORDER_PROPERTY)) {
				advisorDefinition.getPropertyValues().add(
						ORDER_PROPERTY, aspectElement.getAttribute(ORDER_PROPERTY));
			}

			// register the final advisor
			/**
			 * 注册最终顾问
			 *
			 * 生成beanName并把封装好的Advisor定义信息注册到容器中
			 * beanName: ${className}#0, ${className}#1, ${className}#2, ......
			 * 此处是: org.springframework.aop.aspectj.AspectJPointcutAdvisor#*
			 */
			parserContext.getReaderContext().registerWithGeneratedName(advisorDefinition);

			return advisorDefinition;
		}
		finally {
			this.parseState.pop();
		}
	}

	/**
	 * Creates the RootBeanDefinition for a POJO advice bean. Also causes pointcut
	 * parsing to occur so that the pointcut may be associate with the advice bean.
	 * This same pointcut is also configured as the pointcut for the enclosing
	 * Advisor definition using the supplied MutablePropertyValues.
	 * 为 POJO 建议 bean 创建 RootBeanDefinition。还会导致切入点解析发生，以便切入点可以与通知 bean 相关联。
	 * 同样的切入点也被配置为使用提供的 MutablePropertyValues 的封闭顾问定义的切入点。
	 *
	 * 每一个<aop:around/>, <aop:before/>, <aop:after/>, <aop:after-returning/>, <aop:after-throwing/>标签都会被封装成一个Advice对象。
	 *
	 *
	 * 1.解析当前标签，为当前标签创建一个对应实现类的beanDefinition
	 * <aop:around/> : {@link AspectJAroundAdvice}
	 * <aop:before/> : {@link AspectJMethodBeforeAdvice}
	 * <aop:after/> : {@link AspectJAfterAdvice}
	 * <aop:after-returning/> : {@link AspectJAfterReturningAdvice}
	 * <aop:after-throwing/> : {@link AspectJAfterThrowingAdvice}
	 * 默认实现类都继承了{@link org.springframework.aop.aspectj.AbstractAspectJAdvice}
	 *
	 * 2.为beanDefinition设置相关属性
	 * 3.为beanDefinition设置对应实现类构造函数的参数
	 */
	private AbstractBeanDefinition createAdviceDefinition(
			Element adviceElement, ParserContext parserContext, String aspectName, int order,
			RootBeanDefinition methodDef, RootBeanDefinition aspectFactoryDef,
			List<BeanDefinition> beanDefinitions, List<BeanReference> beanReferences) {

		/**
		 * 获取标签对应的实现类类型，并创建对应类型的beanDefinition
		 */
		RootBeanDefinition adviceDefinition = new RootBeanDefinition(getAdviceClass(adviceElement, parserContext));
		adviceDefinition.setSource(parserContext.extractSource(adviceElement));

		/**
		 * 设置切面
		 */
		adviceDefinition.getPropertyValues().add(ASPECT_NAME_PROPERTY, aspectName);
		adviceDefinition.getPropertyValues().add(DECLARATION_ORDER_PROPERTY, order);

		/**
		 * 设置returning， throwing， arg-names属性
		 */
		if (adviceElement.hasAttribute(RETURNING)) {
			adviceDefinition.getPropertyValues().add(
					RETURNING_PROPERTY, adviceElement.getAttribute(RETURNING));
		}
		if (adviceElement.hasAttribute(THROWING)) {
			adviceDefinition.getPropertyValues().add(
					THROWING_PROPERTY, adviceElement.getAttribute(THROWING));
		}
		if (adviceElement.hasAttribute(ARG_NAMES)) {
			adviceDefinition.getPropertyValues().add(
					ARG_NAMES_PROPERTY, adviceElement.getAttribute(ARG_NAMES));
		}

		/**
		 * 设置实现类构造函数参数
		 * {@link org.springframework.aop.aspectj.AbstractAspectJAdvice}类型的实现类都没有默认构造函数，
		 * 只有一个三个参数的构造函数
		 * 
		 * {@link AspectJMethodBeforeAdvice#AspectJMethodBeforeAdvice(Method, AspectJExpressionPointcut, AspectInstanceFactory)}
		 * {@link AspectJAroundAdvice#AspectJAroundAdvice(Method, AspectJExpressionPointcut, AspectInstanceFactory)}
		 * {@link AspectJAfterAdvice#AspectJAfterAdvice(Method, AspectJExpressionPointcut, AspectInstanceFactory)}
		 * {@link AspectJAfterThrowingAdvice#AspectJAfterThrowingAdvice(Method, AspectJExpressionPointcut, AspectInstanceFactory)}
		 * {@link AspectJAfterReturningAdvice#AspectJAfterReturningAdvice(Method, AspectJExpressionPointcut, AspectInstanceFactory)}
		 *
		 * Method: 切点要执行的方法
		 * AspectJExpressionPointcut: 切入点表达式
		 * AspectInstanceFactory: 获取代理类实例的工厂
		 */
		ConstructorArgumentValues cav = adviceDefinition.getConstructorArgumentValues();

		/**
		 * 设置切点要执行的方法
		 */
		cav.addIndexedArgumentValue(METHOD_INDEX, methodDef);

		/**
		 * 解析并设置切入点
		 */
		Object pointcut = parsePointcutProperty(adviceElement, parserContext);
		if (pointcut instanceof BeanDefinition) {
			cav.addIndexedArgumentValue(POINTCUT_INDEX, pointcut);
			beanDefinitions.add((BeanDefinition) pointcut);
		}
		else if (pointcut instanceof String) {
			RuntimeBeanReference pointcutRef = new RuntimeBeanReference((String) pointcut);
			cav.addIndexedArgumentValue(POINTCUT_INDEX, pointcutRef);
			beanReferences.add(pointcutRef);
		}

		/**
		 * 设置代理实例工厂
		 */
		cav.addIndexedArgumentValue(ASPECT_INSTANCE_FACTORY_INDEX, aspectFactoryDef);

		return adviceDefinition;
	}

	/**
	 * Gets the advice implementation class corresponding to the supplied {@link Element}.
	 * 获取与提供的 {@link Element} 对应的建议实现类。
	 */
	private Class<?> getAdviceClass(Element adviceElement, ParserContext parserContext) {
		String elementName = parserContext.getDelegate().getLocalName(adviceElement);
		if (BEFORE.equals(elementName)) {
			return AspectJMethodBeforeAdvice.class;
		}
		else if (AFTER.equals(elementName)) {
			return AspectJAfterAdvice.class;
		}
		else if (AFTER_RETURNING_ELEMENT.equals(elementName)) {
			return AspectJAfterReturningAdvice.class;
		}
		else if (AFTER_THROWING_ELEMENT.equals(elementName)) {
			return AspectJAfterThrowingAdvice.class;
		}
		else if (AROUND.equals(elementName)) {
			return AspectJAroundAdvice.class;
		}
		else {
			throw new IllegalArgumentException("Unknown advice kind [" + elementName + "].");
		}
	}

	/**
	 * Parses the supplied {@code <pointcut>} and registers the resulting
	 * Pointcut with the BeanDefinitionRegistry.
	 * 解析提供的 {@code <pointcut>} 并将生成的切入点注册到 BeanDefinitionRegistry。
	 *
	 * 1.根据切点表达式创建{@link AspectJExpressionPointcut}类型的beanDefinition
	 * 2.把beanDefinition注入到bean工厂
	 * 3.把beanDefinition封装成{@link PointcutComponentDefinition}注入组件定义
	 *
	 */
	private AbstractBeanDefinition parsePointcut(Element pointcutElement, ParserContext parserContext) {
		String id = pointcutElement.getAttribute(ID);
		String expression = pointcutElement.getAttribute(EXPRESSION);

		AbstractBeanDefinition pointcutDefinition = null;

		try {
			this.parseState.push(new PointcutEntry(id));
			/**
			 * 根据表达式创建一个{@link AspectJExpressionPointcut}类型的beanDefinition
			 */
			pointcutDefinition = createPointcutDefinition(expression);
			pointcutDefinition.setSource(parserContext.extractSource(pointcutElement));

			/**
			 * 把beanDefinition注册到bean工厂中
			 * 如果切入点有id属性，就用id属性值作为beanDefinition的名称，如果没有则使用命名生成器生成一个名称，并注册到bean工厂
			 */
			String pointcutBeanName = id;
			if (StringUtils.hasText(pointcutBeanName)) {
				parserContext.getRegistry().registerBeanDefinition(pointcutBeanName, pointcutDefinition);
			}
			else {
				pointcutBeanName = parserContext.getReaderContext().registerWithGeneratedName(pointcutDefinition);
			}

			/**
			 * 把切入点beanDefinition封装成{@link PointcutComponentDefinition}注入组件定义
			 */
			parserContext.registerComponent(
					new PointcutComponentDefinition(pointcutBeanName, pointcutDefinition, expression));
		}
		finally {
			this.parseState.pop();
		}

		return pointcutDefinition;
	}

	/**
	 * Parses the {@code pointcut} or {@code pointcut-ref} attributes of the supplied
	 * {@link Element} and add a {@code pointcut} property as appropriate. Generates a
	 * {@link org.springframework.beans.factory.config.BeanDefinition} for the pointcut if  necessary
	 * and returns its bean name, otherwise returns the bean name of the referred pointcut.
	 */
	@Nullable
	private Object parsePointcutProperty(Element element, ParserContext parserContext) {
		if (element.hasAttribute(POINTCUT) && element.hasAttribute(POINTCUT_REF)) {
			parserContext.getReaderContext().error(
					"Cannot define both 'pointcut' and 'pointcut-ref' on <advisor> tag.",
					element, this.parseState.snapshot());
			return null;
		}
		else if (element.hasAttribute(POINTCUT)) {
			// Create a pointcut for the anonymous pc and register it.
			String expression = element.getAttribute(POINTCUT);
			AbstractBeanDefinition pointcutDefinition = createPointcutDefinition(expression);
			pointcutDefinition.setSource(parserContext.extractSource(element));
			return pointcutDefinition;
		}
		else if (element.hasAttribute(POINTCUT_REF)) {
			String pointcutRef = element.getAttribute(POINTCUT_REF);
			if (!StringUtils.hasText(pointcutRef)) {
				parserContext.getReaderContext().error(
						"'pointcut-ref' attribute contains empty value.", element, this.parseState.snapshot());
				return null;
			}
			return pointcutRef;
		}
		else {
			parserContext.getReaderContext().error(
					"Must define one of 'pointcut' or 'pointcut-ref' on <advisor> tag.",
					element, this.parseState.snapshot());
			return null;
		}
	}

	/**
	 * Creates a {@link BeanDefinition} for the {@link AspectJExpressionPointcut} class using
	 * the supplied pointcut expression.
	 *
	 * 使用提供的切入点表达式为 {@link AspectJExpressionPointcut} 类创建一个 {@link BeanDefinition}。
	 */
	protected AbstractBeanDefinition createPointcutDefinition(String expression) {
		RootBeanDefinition beanDefinition = new RootBeanDefinition(AspectJExpressionPointcut.class);
		beanDefinition.setScope(BeanDefinition.SCOPE_PROTOTYPE);
		beanDefinition.setSynthetic(true);
		beanDefinition.getPropertyValues().add(EXPRESSION, expression);
		return beanDefinition;
	}

}
