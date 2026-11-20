package com.ticketflow.payment.config;

import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

@EnableWs
@Configuration
public class WebServiceConfig {

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(ApplicationContext ctx) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(ctx);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    // Bean name "payment" => WSDL served at /ws/payment.wsdl
    @Bean(name = "payment")
    public DefaultWsdl11Definition paymentWsdl(XsdSchema paymentSchema) {
        DefaultWsdl11Definition wsdl = new DefaultWsdl11Definition();
        wsdl.setPortTypeName("PaymentPort");
        wsdl.setLocationUri("/ws");
        wsdl.setTargetNamespace("http://ticketflow.com/payment/v1");
        wsdl.setSchema(paymentSchema);
        return wsdl;
    }

    @Bean
    public XsdSchema paymentSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/payment.xsd"));
    }
}
