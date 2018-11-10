package me.drton.jmavlib.log.ulog;

import javax.swing.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ulog2csv {
    private JButton Button;
    private JTextArea TextArea;
public String[] nothing = new String[]{"1"};

    public ulog2csv() {
        // ClassLoader classLoader = ULogReader.class.getClassLoader();
        //  Class<?> loadClass = classLoader.loadClass("jMAVlib.src.me.drton.jmavlib.log.ulog.ULogReader");
        // Method method = loadClass.getMethod("main", String[].class);
        // method.invoke(null, new Object[] { new String[] {} });
        try {
            ULogReader.main(nothing);
        } catch (Exception e) {
            System.out.println("aoh");
        }

    }
}
