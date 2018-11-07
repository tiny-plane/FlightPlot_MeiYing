package me.drton.flightplot;

import java.io.*;

public class Filewriter extends Filecontrol{

    String filename;
    String content;

public boolean filewrite(String filename,String content){
    try {
        build_file(filename, content);
    } catch (IOException e){
        return false;
    }
        return  true;
    }

    @Override
    public boolean build_file(String file_name,String content) throws IOException {
        File file = new File("D:" + File.separator + file_name);

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
            Writer writer = null;
            try{
                writer = new FileWriter(file);
             } catch (FileNotFoundException e){
                e.printStackTrace();
             }
            this.writefile(writer,content);
        } catch (Exception e){
            e.printStackTrace();
            return false;
        }
        return true;
    }

    public boolean writefile(Writer writer,String content) throws IOException {

        try{
           // System.out.println("尝试");
           writer.write(content);
         //   System.out.println(content);
        }catch (NullPointerException e) {
            System.out.println("炸了");
            e.printStackTrace();
            try {
                writer.close();
           //     System.out.println("但是关掉了");
            }catch (NullPointerException e1){
          //      System.out.println("关都关不掉");
                return false;
            }
            return false;
        }
      //  System.out.println("write done");
        try {
            writer.close();
        //    System.out.println("写好关掉了");
        } catch (NullPointerException e){
       //     System.out.println("写好了但是关不掉");
            return  false;
        }
        return true;
    }
}
