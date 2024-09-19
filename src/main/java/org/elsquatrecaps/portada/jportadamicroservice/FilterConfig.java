package org.elsquatrecaps.portada.jportadamicroservice;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<SignatureVerificationFilter> signatureFilter() {
        FilterRegistrationBean<SignatureVerificationFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new SignatureVerificationFilter());
        registrationBean.addUrlPatterns("/pr/*");  // Aplica el filtre a aquestes rutes
        return registrationBean;
    }
}
