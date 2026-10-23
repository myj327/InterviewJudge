package com.decade.doj.submission.service;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;
public class TxFixture extends AbstractPlatformTransactionManager {
    public int commits, rollbacks;
    public static <T> T proxy(T target,TxFixture manager) {
        ProxyFactory factory=new ProxyFactory(target);factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        return (T)factory.getProxy();
    }
    protected Object doGetTransaction(){return new Object();}
    protected void doBegin(Object tx,TransactionDefinition definition){}
    protected void doCommit(DefaultTransactionStatus status){commits++;}
    protected void doRollback(DefaultTransactionStatus status){rollbacks++;}
}
