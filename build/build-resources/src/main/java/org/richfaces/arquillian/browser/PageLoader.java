package org.richfaces.arquillian.browser;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

import org.jboss.arquillian.drone.spi.DroneInstanceEnhancer;
import org.jboss.arquillian.drone.spi.InstanceOrCallableInstance;
import org.jboss.arquillian.graphene.proxy.GrapheneProxyUtil;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;

public class PageLoader implements DroneInstanceEnhancer<WebDriver> {

    @Override
    public boolean canEnhance(InstanceOrCallableInstance instance, Class<?> droneType, Class<? extends Annotation> qualifier) {
        return WebDriver.class.isAssignableFrom(droneType);
    }

    @Override
    public WebDriver deenhance(WebDriver enhancedInstance, Class<? extends Annotation> qualifier) {
        if (enhancedInstance instanceof PageLoader) {
            return ((PageReloader) enhancedInstance).getOriginalInstance();
        } else {
            return enhancedInstance;
        }
    }

    @Override
    public WebDriver enhance(WebDriver instance, Class<? extends Annotation> qualifier) {
        return (WebDriver) Proxy.newProxyInstance(getClass().getClassLoader(), getClasses(instance), new PageReloaderImpl(instance));

    }

    private Class<?>[] getClasses(WebDriver instance) {
        // reuse Graphene utils
        Class<?>[] interfaces = GrapheneProxyUtil.getInterfaces(instance.getClass());
        Class<?>[] result = Arrays.copyOf(interfaces, interfaces.length + 1);
        result[interfaces.length] = PageReloader.class;
        return result;
    }

    @Override
    public int getPrecedence() {
        return -101;// run after Graphene (-100)
    }

    public interface PageReloader {

        public WebDriver getOriginalInstance();

        public void tryToLoadPage(String url);
    }

    public static class PageReloaderImpl implements PageReloader, InvocationHandler {

        private static final int DEFAULT_NUMBER_OF_REPEATS = 3;
        private static final String GET_METHOD_NAME = "get";

        private final WebDriver originalInstance;

        public PageReloaderImpl(WebDriver originalInstance) {
            this.originalInstance = originalInstance;
        }

        @Override
        public WebDriver getOriginalInstance() {
            return originalInstance;
        }

        /**
         * Proxies the "get" method of WebDriver.
         */
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals(GET_METHOD_NAME)) {
                tryToLoadPage(String.valueOf(args[0]));
                return null;
            } else {
                return method.invoke(getOriginalInstance(), args);
            }
        }

        /**
         * Tries to load the page repeatedly.
         *
         * @see https://issues.jboss.org/browse/RF-14292
         *
         * @param url url to be opened
         */
        @Override
        public void tryToLoadPage(String url) {
            for (int i = 1; i <= DEFAULT_NUMBER_OF_REPEATS; i++) {
                try {
                    getOriginalInstance().get(url);
                    return;
                } catch (TimeoutException e) {
                    if (i == DEFAULT_NUMBER_OF_REPEATS) {
                        throw e;
                    }
                    System.out.println("Page was not loaded withinn timeout. Trying to load it again, attempt #" + (i + 1));
                }
            }
        }
    }
}
