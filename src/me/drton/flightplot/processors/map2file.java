package me.drton.flightplot.processors;

import java.io.FileWriter;
import java.util.Map;
import java.io.IOException;
import java.io.File;
import java.io.*;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.FileReader;

public class map2file {

    public File mapTofile(Map<String, String> map){
        File file = new File("D:" + File.separator + "FlightPlot_out.m");
        try {
            writer(map, file);
        } catch (IOException e){
            System.out.println("aho");
        }
        return file;
    }

    private File writer(Map<String, String> map, File file) throws IOException{
        StringBuffer buffer = new StringBuffer();
        FileWriter writer = new FileWriter(file, false);
        BufferedWriter nl = new BufferedWriter(writer);

        for(Map.Entry entry:map.entrySet()){
            String key = (String) entry.getKey();
            String value = (String) entry.getValue();
            buffer.append(key + ":" + value);
            nl.newLine();
        }
        writer.write(buffer.toString());
        writer.close();
        return file;

    }
}
