package org.btbox.common.license.config;


import org.btbox.common.license.interceptor.LicenseCheckInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * @ProjectName WebMvcConfig
 * @author Administrator
 * @version 1.0.0
 * @Description 注册拦截器
 * @createTime 2022/4/30 0030 21:11
 */
@Configuration
public class LicenseWebConfig implements WebMvcConfigurer {

    /**
     * 添加拦截器
     */
    @Bean
    public LicenseCheckInterceptor licenseCheckInterceptor() {
        return new LicenseCheckInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this.licenseCheckInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/license/**");
    }
}
