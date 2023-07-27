/*
 * Copyright (c) 2010-2019 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.authentication.impl.factory.module;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.ObjectPostProcessor;
import org.springframework.stereotype.Component;

import com.evolveum.midpoint.authentication.api.AuthenticationChannel;
import com.evolveum.midpoint.authentication.impl.module.authentication.HintAuthenticationModuleAuthentication;
import com.evolveum.midpoint.authentication.impl.module.authentication.ModuleAuthenticationImpl;
import com.evolveum.midpoint.authentication.impl.module.configuration.LoginFormModuleWebSecurityConfiguration;
import com.evolveum.midpoint.authentication.impl.module.configurer.HintModuleWebSecurityConfigurer;
import com.evolveum.midpoint.authentication.impl.provider.HintAuthenticationProvider;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;

@Component
public class HintAuthenticationModuleFactoryImpl extends AbstractCredentialModuleFactory<
        LoginFormModuleWebSecurityConfiguration,
        HintModuleWebSecurityConfigurer<LoginFormModuleWebSecurityConfiguration>,
        HintAuthenticationModuleType,
        HintAuthenticationModuleAuthentication> {

    @Override
    public boolean match(AbstractAuthenticationModuleType moduleType, AuthenticationChannel authenticationChannel) {
        return moduleType instanceof HintAuthenticationModuleType;
    }

    @Override
    protected LoginFormModuleWebSecurityConfiguration createConfiguration(HintAuthenticationModuleType moduleType, String prefixOfSequence, AuthenticationChannel authenticationChannel) {
        LoginFormModuleWebSecurityConfiguration configuration = LoginFormModuleWebSecurityConfiguration.build(moduleType,prefixOfSequence);
        configuration.setSequenceSuffix(prefixOfSequence);
        return configuration;
    }

    @Override
    protected HintModuleWebSecurityConfigurer<LoginFormModuleWebSecurityConfiguration> createModule(
            LoginFormModuleWebSecurityConfiguration configuration) {
        return  getObjectObjectPostProcessor().postProcess(new HintModuleWebSecurityConfigurer<>(configuration));
    }

    @Override
    protected HintModuleWebSecurityConfigurer<LoginFormModuleWebSecurityConfiguration> createModuleConfigurer(HintAuthenticationModuleType moduleType, String sequenceSuffix, AuthenticationChannel authenticationChannel, ObjectPostProcessor<Object> objectPostProcessor) {
        return new HintModuleWebSecurityConfigurer<>(moduleType, sequenceSuffix, authenticationChannel, objectPostProcessor);
    }

    @Override
    protected AuthenticationProvider createProvider(CredentialPolicyType usedPolicy) {
        return new HintAuthenticationProvider();
    }

    @Override
    protected Class<? extends CredentialPolicyType> supportedClass() {
        return null;
    }

    @Override
    protected HintAuthenticationModuleAuthentication createEmptyModuleAuthentication(HintAuthenticationModuleType moduleType,
            LoginFormModuleWebSecurityConfiguration configuration, AuthenticationSequenceModuleType sequenceModule) {
        HintAuthenticationModuleAuthentication moduleAuthentication = new HintAuthenticationModuleAuthentication(sequenceModule);
        moduleAuthentication.setPrefix(configuration.getPrefixOfModule());
        moduleAuthentication.setCredentialName(moduleType.getCredentialName());
        moduleAuthentication.setCredentialType(supportedClass());
        moduleAuthentication.setNameOfModule(configuration.getModuleIdentifier());
        return moduleAuthentication;
    }


}
