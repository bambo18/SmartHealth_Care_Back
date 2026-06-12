package com.smarthealthdog.backend.config;

import java.util.Properties;

import javax.sql.DataSource;

import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Trigger;
import org.quartz.spi.JobFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.quartz.QuartzProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;
import org.springframework.transaction.PlatformTransactionManager;

import com.smarthealthdog.backend.jobs.PushAIInferenceTasks;
import com.smarthealthdog.backend.jobs.RemoveServiceFailureAIInferenceTasks;
import com.smarthealthdog.backend.jobs.RetryAIInferenceTasks;


@Configuration
@Profile("dev")
public class QuartzConfig {

    @Autowired
    private QuartzProperties quartzProperties;

    @Bean
    public SchedulerFactoryBean schedulerFactoryBean(
            DataSource dataSource, 
            PlatformTransactionManager transactionManager, 
            JobFactory jobFactory, 
            Trigger pushAIInferenceTaskTrigger,
            Trigger removeServiceErrorTasksTrigger,
            Trigger retryJobTrigger
    ) {
        Properties properties = new Properties();
        properties.putAll(quartzProperties.getProperties());

        SchedulerFactoryBean scheduler = new SchedulerFactoryBean();
        scheduler.setDataSource(dataSource);
        scheduler.setTransactionManager(transactionManager);
        scheduler.setJobFactory(jobFactory);
        scheduler.setTriggers(pushAIInferenceTaskTrigger, removeServiceErrorTasksTrigger, retryJobTrigger);
        scheduler.setQuartzProperties(properties);

        return scheduler;
    }

    @Bean
    public CronTrigger pushAIInferenceTaskTrigger(JobDetail aiInferenceJobDetail) {
        CronTriggerFactoryBean cronTriggerFactoryBean = new CronTriggerFactoryBean();
        cronTriggerFactoryBean.setJobDetail(aiInferenceJobDetail);
        cronTriggerFactoryBean.setCronExpression("0/20 * * * * ?"); // For testing: run every 20 seconds

        try {
            cronTriggerFactoryBean.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up cron trigger", e);
        }

        return cronTriggerFactoryBean.getObject();
    }

    @Bean
    public CronTrigger removeServiceErrorTasksTrigger(JobDetail removeServiceErrorTasksJobDetail) {
        CronTriggerFactoryBean cronTriggerFactoryBean = new CronTriggerFactoryBean();
        cronTriggerFactoryBean.setJobDetail(removeServiceErrorTasksJobDetail);
        
        // Delete every 10 minutes
        cronTriggerFactoryBean.setCronExpression("0 0/10 * * * ?");
        try {
            cronTriggerFactoryBean.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up remove service error tasks cron trigger", e);
        }
        return cronTriggerFactoryBean.getObject();
    }

    @Bean
    public CronTrigger retryJobTrigger(JobDetail retryAIInferenceJobDetail) {
        CronTriggerFactoryBean cronTriggerFactoryBean = new CronTriggerFactoryBean();
        cronTriggerFactoryBean.setJobDetail(retryAIInferenceJobDetail);
        cronTriggerFactoryBean.setCronExpression("0 0/1 * * * ?"); // For testing: run every minute

        try {
            cronTriggerFactoryBean.afterPropertiesSet();
        } catch (Exception e) {
            throw new RuntimeException("Failed to set up retry cron trigger", e);
        }

        return cronTriggerFactoryBean.getObject();
    }

    @Bean
    public JobDetail aiInferenceJobDetail() {
        JobDetailFactoryBean jobDetailFactoryBean = new JobDetailFactoryBean();
        jobDetailFactoryBean.setJobClass(PushAIInferenceTasks.class);
        jobDetailFactoryBean.setName("PushAIInferenceTasksJob");
        jobDetailFactoryBean.setDescription("Job to push AI inference tasks");
        jobDetailFactoryBean.setDurability(true);
        jobDetailFactoryBean.afterPropertiesSet();

        return jobDetailFactoryBean.getObject();
    }

    @Bean
    public JobDetail removeServiceErrorTasksJobDetail() {
        JobDetailFactoryBean jobDetailFactoryBean = new JobDetailFactoryBean();
        jobDetailFactoryBean.setJobClass(RemoveServiceFailureAIInferenceTasks.class);
        jobDetailFactoryBean.setName("RemoveServiceErrorTasksJob");
        jobDetailFactoryBean.setDescription("Job to remove service error tasks");
        jobDetailFactoryBean.setDurability(true);
        jobDetailFactoryBean.afterPropertiesSet();

        return jobDetailFactoryBean.getObject();
    }

    @Bean
    public JobDetail retryAIInferenceJobDetail() {
        JobDetailFactoryBean jobDetailFactoryBean = new JobDetailFactoryBean();
        jobDetailFactoryBean.setJobClass(RetryAIInferenceTasks.class);
        jobDetailFactoryBean.setName("RetryAIInferenceTasksJob");
        jobDetailFactoryBean.setDescription("Job to retry AI inference tasks");
        jobDetailFactoryBean.setDurability(true);
        jobDetailFactoryBean.afterPropertiesSet();

        return jobDetailFactoryBean.getObject();
    }

    @Bean
    public JobFactory jobFactory(ApplicationContext applicationContext) {
        return new SpringBeanJobFactory();
    }
}
