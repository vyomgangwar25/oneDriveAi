package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
// auditorAwareRef tells Spring which bean provides current user info
public class AuditConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        /* Provides the current auditor (user)
         Used by @CreatedBy and @LastModifiedBy annotations
         */
        return () -> Optional.of("SYSTEM");
    }
}

/*
 * Enables Spring Data JPA Auditing.
 *
 * What happens behind the scenes:
 *
 * 1. Whenever save() or update() happens:
 *
 *      userRepository.save(user)
 *
 * 2. Hibernate triggers Entity Lifecycle Events
 *    (Before Insert / Before Update)
 *
 * 3. AuditingEntityListener gets called automatically
 *    because of:
 *
 *      @EntityListeners(AuditingEntityListener.class)
 *
 * 4. Spring checks audit annotations:
 *
 *      @CreatedDate
 *      @LastModifiedDate
 *      @CreatedBy
 *      @LastModifiedBy
 *
 * 5. For @CreatedBy and @LastModifiedBy,
 *    Spring calls:
 *
 *      auditorProvider()
 *
 * 6. Whatever value this method returns
 *    gets stored automatically in DB.
 *
 * Example:
 *
 *      Optional.of("SYSTEM")
 *
 * DB:
 *      created_by = SYSTEM
 *      updated_by = SYSTEM
 *
 * Later with JWT:
 *
 *      created_by = vyom@gmail.com
 */
