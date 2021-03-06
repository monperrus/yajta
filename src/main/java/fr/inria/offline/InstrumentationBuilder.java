package fr.inria.offline;

import fr.inria.yajta.Utils;
import fr.inria.yajta.api.ClassList;
import fr.inria.yajta.api.MalformedTrackingClassException;
import fr.inria.yajta.api.SimpleTracer;
import fr.inria.yajta.api.Tracking;
import fr.inria.yajta.api.ValueTracking;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

public class InstrumentationBuilder {
    File classDir;
    File outputDir;
    boolean tmpOutput = false;
    Class loggerClass;
    ClassList list;
    SimpleTracer tracer;

    public InstrumentationBuilder (File classDir, File outputDir, ClassList filter, Class trackingClass) throws MalformedTrackingClassException {
        this.classDir = classDir;
        this.outputDir = outputDir;
        this.list = filter;
        this.loggerClass = trackingClass;
        tracer = new SimpleTracer(list,null);
        if(implementsInterface(trackingClass, Tracking.class)) {
            tracer.setTrackingClass(trackingClass);
        } else if (implementsInterface(trackingClass, ValueTracking.class)) {
            tracer.setValueTrackingClass(trackingClass);
        } else {
            throw new MalformedTrackingClassException("Tracking class must implements either Tracking or ValueTracking");
        }
        if(outputDir == null) {
            tmpOutput = true;
            this.outputDir = Utils.getATmpDir();
        }
    }

    public InstrumentationBuilder (File classDir, Class trackingClass) throws MalformedTrackingClassException {
        this(classDir,null,null,trackingClass);
    }

    public InstrumentationBuilder (File classDir, ClassList filter, Class trackingClass) throws MalformedTrackingClassException {
        this(classDir,null,filter,trackingClass);
    }

    public InstrumentationBuilder (File classDir, File outputDir, Class trackingClass) throws MalformedTrackingClassException {
        this(classDir,outputDir,null,trackingClass);
    }

    public String[] filter(String[]classes , ClassList filter) {
        if(filter == null) return classes;
        return Arrays.asList(classes).stream().filter(c -> filter.isToBeProcessed(c)).toArray(size -> new String[size]);
    }


    public void instrument() throws MalformedTrackingClassException {
        try {
            if(implementsInterface(loggerClass,Tracking.class)) {
                tracer.setTrackingClass(loggerClass);
            } else if (implementsInterface(loggerClass,ValueTracking.class)) {
                tracer.setValueTrackingClass(loggerClass);
            } else {
                throw new MalformedTrackingClassException("logger class must be a class that implements either Tracking or ValueTracking");
            }
            ClassPool pool = ClassPool.getDefault();
            pool.appendClassPath(InstrumentationBuilder.class.getProtectionDomain().getCodeSource().getLocation().getPath());
            pool.appendClassPath(loggerClass.getProtectionDomain().getCodeSource().getLocation().getPath());
            pool.appendClassPath(classDir.getAbsolutePath());
            String[] classNames = filter(Utils.listClassesAsArray(classDir), list);
            CtClass[] classToTransform = pool.get(classNames);

            for(CtClass cl: classToTransform) {
                try {
                    tracer.doClass(cl,cl.getName());
                    cl.writeFile(outputDir.getAbsolutePath());
                } catch (CannotCompileException e) {
                    throw new MalformedTrackingClassException("Incorrect probe insertion.");
                } catch (IOException e) {
                    throw new MalformedTrackingClassException("ClassFile not found?");
                }
            }

        } catch (NotFoundException e ) {
            throw new MalformedTrackingClassException("");
        }
    }


    public boolean implementsInterface(Class cl, Class interf) {
        for(Class c : cl.getInterfaces()) {
            if(c.equals(interf)) {
                return true;
            }
        }
        return false;
    }

    //EntryPoint
    String className;
    String methodName;
    Class<?>[] paramtersType;
    public void setEntryPoint(String className, String methodName, Class<?>... paramtersType) {
        this.className = className;
        this.methodName = methodName;
        this.paramtersType = paramtersType;
    }

    public void runInstrumented(Object... paramters) throws MalformedTrackingClassException {
        try {

            URLClassLoader urlClassLoader = URLClassLoader.newInstance(new URL[]{outputDir.toURI().toURL()});
            Class appClass = urlClassLoader.loadClass(className);
            Method method;
            method = appClass.getMethod(methodName, paramtersType);
            method.invoke(null, paramters);

        } catch (MalformedURLException |
                ClassNotFoundException |
                NoSuchMethodException |
                InvocationTargetException |
                IllegalAccessException e ) {
            throw new MalformedTrackingClassException("");
        }
    }

    public void close() {
        if(tmpOutput && outputDir.exists()) {
            outputDir.delete();
        }
    }

    @Override
    public void finalize() throws Throwable {
        if(tmpOutput && outputDir.exists()) {
            outputDir.delete();
        }
        super.finalize();
    }
}
