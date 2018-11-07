package me.drton.flightplot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.Writer;
import java.io.OutputStream;

/** Di */
/** 用于文件读写*/
public class Filecontrol {

    public Filecontrol(){
            super();
    }


    public void Filecontrol()  {
        String file_data = "test";
        String file_name = "test.txt";
        System.out.println("laile");
        try {
            this.build_file(file_name, file_data);
        } catch (IOException e){
    }
    }

    public boolean build_file(String file_name,String file_data) throws IOException {
        File file = new File("D:" + File.separator + file_name);
        System.out.println("wenjian");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                file.delete();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            this.write_file(file_name,file_data);
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean write_file(String file_name,String file_data) throws IOException {
        File file = new File("D:" + File.separator + file_name);
        Writer writer = null;
        System.out.println("xieru");
        try{
            writer = new FileWriter(file);
        } catch (FileNotFoundException e){
            e.printStackTrace();
        }
        try{
            writer.write(file_data);
        }catch (IOException e) {
            e.printStackTrace();
            writer.close();
            return false;
        }
        writer.close();
        return true;
    }
}
