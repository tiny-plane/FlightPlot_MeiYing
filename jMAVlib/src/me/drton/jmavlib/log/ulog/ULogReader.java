package me.drton.jmavlib.log.ulog;
//这个是ulog格式的阅读器
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.io.*;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.filechooser.FileSystemView;

import me.drton.jmavlib.log.BinaryLogReader;
import me.drton.jmavlib.log.FormatErrorException;

/**
 * User: ton Date: 03.06.13 Time: 14:18
 */
public class ULogReader extends BinaryLogReader {//定义报头
    public int counter = 0;
    static final byte MESSAGE_TYPE_FORMAT = (byte) 'F';
    static final byte MESSAGE_TYPE_DATA = (byte) 'D';
    static final byte MESSAGE_TYPE_INFO = (byte) 'I';
    static final byte MESSAGE_TYPE_INFO_MULTIPLE = (byte) 'M';
    static final byte MESSAGE_TYPE_PARAMETER = (byte) 'P';
    static final byte MESSAGE_TYPE_ADD_LOGGED_MSG = (byte) 'A';
    static final byte MESSAGE_TYPE_REMOVE_LOGGED_MSG = (byte) 'R';
    static final byte MESSAGE_TYPE_SYNC = (byte) 'S';
    static final byte MESSAGE_TYPE_DROPOUT = (byte) 'O';
    static final byte MESSAGE_TYPE_LOG = (byte) 'L';
    static final byte MESSAGE_TYPE_FLAG_BITS = (byte) 'B';
    static final int HDRLEN = 3;
    static final int FILE_MAGIC_HEADER_LENGTH = 16;
    public File fileraw = new File("D:" + File.separator + "matlabraw.txt");
    static final int INCOMPAT_FLAG0_DATA_APPENDED_MASK = 1<<0;
    public FileWriter writer = new FileWriter(fileraw,true);
    private String systemName = "PX4";
    private long dataStart = 0;
    private Map<String, MessageFormat> messageFormats = new HashMap<String, MessageFormat>();
  //  public StringBuffer buffer = new StringBuffer();
   public BufferedWriter nl = new BufferedWriter(writer);

    private class Subscription {
        public Subscription(MessageFormat f, int multiID) {
            this.format = f;
            this.multiID = multiID;
        }
        public MessageFormat format;
        public int multiID;
    }

    /** all subscriptions. Index is the message id */
    private ArrayList<Subscription> messageSubscriptions = new ArrayList<Subscription>();

    private Map<String, String> fieldsList = null;
    private long sizeUpdates = -1;
    private long sizeMicroseconds = -1;
    private long startMicroseconds = -1;
    private long utcTimeReference = -1;
    private long logStartTimestamp = -1;
    private boolean nestedParsingDone = false;
    private Map<String, Object> version = new HashMap<String, Object>();//版本
    private Map<String, Object> parameters = new HashMap<String, Object>();//参数
    public ArrayList<MessageLog> loggedMessages = new ArrayList<MessageLog>();//日志信息

    private String hardfaultPlainText = "";

    private Vector<Long> appendedOffsets = new Vector<Long>();
    int currentAppendingOffsetIndex = 0; // current index to appendedOffsets for the next appended offset

    public Map<String, List<ParamUpdate>> parameterUpdates;
    private boolean replayedLog = false;
    public class ParamUpdate {
        private String name;
        private Object value;
        private long timestamp = -1;
        private ParamUpdate(String nm, Object v, long ts) {//按照这格式把变量放到容器内
            name = nm;
            value = v;
            timestamp = ts;
        }

        public String getName() {
            return name;
        }

        public Object getValue() {
            return value;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }//以上是分开获取
    private List<Exception> errors = new ArrayList<Exception>();//如果得到错误，记录在这个里面
    private int logVersion = 0;
    private int headerSize = 2;//报头大小

    /** Index for fast(er) seeking */
    private ArrayList<SeekTime> seekTimes = null;

    private class SeekTime {
        public SeekTime(long t, long pos) {
            timestamp = t;
            position = pos;
        }

        public long timestamp;
        public long position;
    }

    public ULogReader(String fileName) throws IOException, FormatErrorException {
        super(fileName);
        parameterUpdates = new HashMap<String, List<ParamUpdate>>();//存在一个map里
        updateStatistics();//运行这个来读
    }

    @Override
    public String getFormat() {
        return "ULog v" + logVersion;
    }

    public String getSystemName() {
        return systemName;
    }

    @Override
    public long getSizeUpdates() {
        return sizeUpdates;
    }

    @Override
    public long getStartMicroseconds() {
        return startMicroseconds;
    }

    @Override
    public long getSizeMicroseconds() {
        return sizeMicroseconds;
    }

    @Override
    public long getUTCTimeReferenceMicroseconds() {
        return utcTimeReference;
    }

    @Override
    public Map<String, Object> getVersion() {
        return version;
    }

    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Read and parse the file header.
     * 
     * @throws IOException
     * @throws FormatErrorException
     */
    private void readFileHeader() throws IOException, FormatErrorException {//读报头的部分，检查是否正确
        fillBuffer(FILE_MAGIC_HEADER_LENGTH);
        //magic + version
        boolean error = false;
        if ((buffer.get() & 0xFF) != 'U')
            error = true;
        if ((buffer.get() & 0xFF) != 'L')
            error = true;
        if ((buffer.get() & 0xFF) != 'o')
            error = true;
        if ((buffer.get() & 0xFF) != 'g')
            error = true;
        if ((buffer.get() & 0xFF) != 0x01)
            error = true;
        if ((buffer.get() & 0xFF) != 0x12)
            error = true;
        if ((buffer.get() & 0xFF) != 0x35)
            error = true;
        if ((buffer.get() & 0xFF) > 0x01 && !error) {
            System.out.println("ULog: Different version than expected. Will try anyway");//只要报头不是上面的那个内容，就打印版本错误
        }
        if (error)
            throw new FormatErrorException("ULog: Wrong file format");//如果有错误就发送格式错误信号

        logStartTimestamp = buffer.getLong();
    }

    /**
     * Read all necessary information from the file, including message formats,
     * seeking positions and log file information.
     * 
     * @throws IOException
     * @throws FormatErrorException
     */
    private void updateStatistics() throws IOException, FormatErrorException {//这里是读内容，也就是调用这个读取方法执行的过程，首先将文件名通过类共用变量传递进来，运行读报头确认可读，否则返回格式错误
        position(0);
        readFileHeader();
        long packetsNum = 0;
        long timeStart = -1;
        long timeEnd = -1;
        long lastTime = -1;
        fieldsList = new HashMap<String, String>();
        seekTimes = new ArrayList<SeekTime>();
        while (true) {
            Object msg;
            long pos = position();
            try {
                msg = readMessage();
            } catch (EOFException e) {
                break;
            }
            packetsNum++;
//msg内部存储了所有参数数量长度等等内容格式等等，还有所有参数,以及记录的变量数据类型长度等等，可以写成一个文件看一下，他们为msg写了tostring方法，可以直接用


            if (msg instanceof MessageFlagBits) {
                MessageFlagBits msgFlags = (MessageFlagBits) msg;
                // check flags
                if ((msgFlags.incompatFlags[0] & INCOMPAT_FLAG0_DATA_APPENDED_MASK) != 0) {
                    for (int i = 0; i < msgFlags.appendedOffsets.length; ++i) {
                        if (msgFlags.appendedOffsets[i] > 0) {
                            appendedOffsets.add(msgFlags.appendedOffsets[i]);
                        }
                    }
                    if (appendedOffsets.size() > 0) {
                        System.out.println("log contains appended data");
                    }
                }
                boolean containsUnknownIncompatBits = false;
                if ((msgFlags.incompatFlags[0] & ~0x1) != 0)
                    containsUnknownIncompatBits = true;
                for (int i = 1; i < msgFlags.incompatFlags.length; ++i) {
                    if (msgFlags.incompatFlags[i] != 0)
                        containsUnknownIncompatBits = true;
                }
                if (containsUnknownIncompatBits) {
                    throw new FormatErrorException("Log contains unknown incompatible bits. Refusing to parse the log.");
                }

            } else if (msg instanceof MessageFormat) {
                MessageFormat msgFormat = (MessageFormat) msg;
                messageFormats.put(msgFormat.name, msgFormat);

            } else if (msg instanceof MessageAddLogged) {
                //from now on we cannot have any new MessageFormat's, so we
                //can parse the nested types
                if (!nestedParsingDone) {
                    for (MessageFormat m : messageFormats.values()) {
                        m.parseNestedTypes(messageFormats);
                    }
                    //now do a 2. pass to remove the last padding field
                    for (MessageFormat m : messageFormats.values()) {
                        m.removeLastPaddingField();
                    }
                    nestedParsingDone = true;
                }
                MessageAddLogged msgAddLogged = (MessageAddLogged) msg;
                MessageFormat msgFormat = messageFormats.get(msgAddLogged.name);
                if(msgFormat == null)
                    throw new FormatErrorException("Format of subscribed message not found: " + msgAddLogged.name);
                Subscription subscription = new Subscription(msgFormat, msgAddLogged.multiID);
                if (msgAddLogged.msgID < messageSubscriptions.size()) {
                    messageSubscriptions.set(msgAddLogged.msgID, subscription);
                } else {
                    while (msgAddLogged.msgID > messageSubscriptions.size())
                        messageSubscriptions.add(null);
                    messageSubscriptions.add(subscription);
                }
                if (msgAddLogged.multiID > msgFormat.maxMultiID)
                    msgFormat.maxMultiID = msgAddLogged.multiID;

            } else if (msg instanceof MessageParameter) {
                MessageParameter msgParam = (MessageParameter) msg;
                // a replayed log can contain many parameter updates, so we ignore them here
                if (parameters.containsKey(msgParam.getKey()) && !replayedLog) {
                    System.out.println("update to parameter: " + msgParam.getKey() + " value: " + msgParam.value + " at t = " + lastTime);
                    // maintain a record of parameters which change during flight
                    if (parameterUpdates.containsKey(msgParam.getKey())) {
                        parameterUpdates.get(msgParam.getKey()).add(new ParamUpdate(msgParam.getKey(), msgParam.value, lastTime));
                    } else {
                        List<ParamUpdate> updateList = new ArrayList<ParamUpdate>();
                        updateList.add(new ParamUpdate(msgParam.getKey(), msgParam.value, lastTime));
                        parameterUpdates.put(msgParam.getKey(), updateList);
                    }
                } else {
                    // add parameter to the parameters Map
                    parameters.put(msgParam.getKey(), msgParam.value);
                }

            } else if (msg instanceof MessageInfo) {
                MessageInfo msgInfo = (MessageInfo) msg;
                if ("sys_name".equals(msgInfo.getKey())) {
                    systemName = (String) msgInfo.value;
                } else if ("ver_hw".equals(msgInfo.getKey())) {
                    version.put("HW", msgInfo.value);
                } else if ("ver_sw".equals(msgInfo.getKey())) {
                    version.put("FW", msgInfo.value);
                } else if ("time_ref_utc".equals(msgInfo.getKey())) {
                    utcTimeReference = ((long) ((Number) msgInfo.value).intValue()) * 1000 * 1000;
                } else if ("replay".equals(msgInfo.getKey())) {
                    replayedLog = true;
                }
            } else if (msg instanceof MessageInfoMultiple) {
                MessageInfoMultiple msgInfo = (MessageInfoMultiple) msg;
                //System.out.println(msgInfo.getKey());
                if ("hardfault_plain".equals(msgInfo.getKey())) {
                    // append all hardfaults to one String (we should be looking at msgInfo.isContinued as well)
                    hardfaultPlainText += (String)msgInfo.value;
                }

            } else if (msg instanceof MessageData) {
                if (dataStart == 0) {
                    dataStart = pos;
                }
                MessageData msgData = (MessageData) msg;
                seekTimes.add(new SeekTime(msgData.timestamp, pos));

                if (timeStart < 0) {
                    timeStart = msgData.timestamp;
                }
                if (timeEnd < msgData.timestamp) timeEnd = msgData.timestamp;
                lastTime = msgData.timestamp;
            } else if (msg instanceof MessageLog) {
                MessageLog msgLog = (MessageLog) msg;
                loggedMessages.add(msgLog);
            }
        }

        // fill the fieldsList now that we know how many multi-instances are in the log
        for (int k = 0; k < messageSubscriptions.size(); ++k) {
            Subscription s = messageSubscriptions.get(k);
            if (s != null) {
                MessageFormat msgFormat = s.format;
                if (msgFormat.name.charAt(0) != '_') {
                    int maxInstance = msgFormat.maxMultiID;
                    for (int i = 0; i < msgFormat.fields.size(); i++) {
                        FieldFormat fieldDescr = msgFormat.fields.get(i);
                        if (!fieldDescr.name.contains("_padding") && fieldDescr.name != "timestamp") {
                            for (int mid = 0; mid <= maxInstance; mid++) {
                                if (fieldDescr.isArray()) {
                                    for (int j = 0; j < fieldDescr.size; j++) {
                                        fieldsList.put(msgFormat.name + "_" + mid + "." + fieldDescr.name + "[" + j + "]", fieldDescr.type);
                                    }
                                } else {
                                    fieldsList.put(msgFormat.name + "_" + mid + "." + fieldDescr.name, fieldDescr.type);
                                }
                            }
                        }
                    }
                }
            }
        }
        startMicroseconds = timeStart;
        sizeUpdates = packetsNum;
        sizeMicroseconds = timeEnd - timeStart;
        seek(0);

        if (!errors.isEmpty()) {
            System.err.println("Errors while reading file:");
            for (final Exception e : errors) {
                System.err.println(e.getMessage());
            }
            errors.clear();
        }

        if (hardfaultPlainText.length() > 0) {
            // TODO: find a better way to show this to the user?
            System.out.println("Log contains hardfault data:");
            System.out.println(hardfaultPlainText);
        }
    }

    @Override
    public boolean seek(long seekTime) throws IOException, FormatErrorException {
        position(dataStart);
        currentAppendingOffsetIndex = 0;

        if (seekTime == 0) {      // Seek to start of log
            return true;
        }

        //find the position in seekTime. We could speed this up further by
        //using a binary search
        for (SeekTime sk : seekTimes) {
            if (sk.timestamp >= seekTime) {
                position(sk.position);
                while(currentAppendingOffsetIndex < appendedOffsets.size() && appendedOffsets.get(currentAppendingOffsetIndex) < sk.position)
                    ++currentAppendingOffsetIndex;
                return true;
            }
        }
        return false;
    }

    private void applyMsg(Map<String, Object> update, MessageData msg) {
        applyMsgAsName(update, msg, msg.format.name + "_" + msg.multiID);
    }

    void applyMsgAsName(Map<String, Object> update, MessageData msg, String msg_name) {
        final ArrayList<FieldFormat> fields = msg.format.fields;
        for (int i = 0; i < fields.size(); i++) {
            FieldFormat field = fields.get(i);
            if (field.isArray()) {
                for (int j = 0; j < field.size; j++) {
                    update.put(msg_name + "." + field.name + "[" + j + "]", ((Object[]) msg.get(i))[j]);
                }
            } else {
                update.put(msg_name + "." + field.name, msg.get(i));
            }
        }
    }

    @Override
    public long readUpdate(Map<String, Object> update) throws IOException, FormatErrorException {
        while (true) {
            Object msg = readMessage();
            if (msg instanceof MessageData) {
                applyMsg(update, (MessageData) msg);
                writer.close();

                return ((MessageData) msg).timestamp;
            }
        }

    }

    @Override
    public Map<String, String> getFields() {
        return fieldsList;
    }

    /**
     * Read next message from log
     *
     * @return log message
     * @throws IOException  on IO error
     * @throws EOFException on end of stream
     */
    public Object readMessage() throws IOException, FormatErrorException {//读消息
        while (true) {
            fillBuffer(HDRLEN);
            long pos = position();
            int s1 = buffer.get() & 0xFF;//补码保证二进制一致性
            int s2 = buffer.get() & 0xFF;
            int msgSize = s1 + (256 * s2);//这里得到信息部分的长度，应该是一个报头s1，每个信息长度256，总共s2个
            int msgType = buffer.get() & 0xFF;//第三个量存储的是信息类型

            // check if we cross an appending boundary: if so, we need to reset the position and skip this message
            if (currentAppendingOffsetIndex < appendedOffsets.size()) {//pos是一个定位文件位置的变量
                if (pos + HDRLEN + msgSize > appendedOffsets.get(currentAppendingOffsetIndex)) {
                    //System.out.println("Jumping to next position: "+pos + ", next: "+appendedOffsets.get(currentAppendingOffsetIndex));
                    position(appendedOffsets.get(currentAppendingOffsetIndex));
                    ++currentAppendingOffsetIndex;
                    continue;
                }
            }

            try {
                fillBuffer(msgSize);
            } catch (EOFException e) {
                errors.add(new FormatErrorException(pos, "Unexpected end of file"));
                throw e;
            }
            Object msg;
            switch (msgType) {//接下来有几种类型的信息
            case MESSAGE_TYPE_DATA:
                s1 = buffer.get() & 0xFF;
                s2 = buffer.get() & 0xFF;
                int msgID = s1 + (256 * s2);
                Subscription subscription = null;
                if (msgID < messageSubscriptions.size())
                    subscription = messageSubscriptions.get(msgID);
                if (subscription == null) {
                    position(pos);
                    errors.add(new FormatErrorException(pos, "Unknown DATA subscription ID: " + msgID));
                    buffer.position(buffer.position() + msgSize - 1);
                    continue;
                }
                msg = new MessageData(subscription.format, buffer, subscription.multiID);
                break;
            case MESSAGE_TYPE_FLAG_BITS:
                msg = new MessageFlagBits(buffer, msgSize);
                break;
            case MESSAGE_TYPE_INFO:
                msg = new MessageInfo(buffer);
                break;
            case MESSAGE_TYPE_INFO_MULTIPLE:
                msg = new MessageInfoMultiple(buffer);
                break;
            case MESSAGE_TYPE_PARAMETER:
                msg = new MessageParameter(buffer);
                break;
            case MESSAGE_TYPE_FORMAT:
                msg = new MessageFormat(buffer, msgSize);
                break;
            case MESSAGE_TYPE_ADD_LOGGED_MSG:
                msg = new MessageAddLogged(buffer, msgSize);
                break;
            case MESSAGE_TYPE_DROPOUT:
                msg = new MessageDropout(buffer);
                break;
            case MESSAGE_TYPE_LOG:
                msg = new MessageLog(buffer, msgSize);
                break;
            case MESSAGE_TYPE_REMOVE_LOGGED_MSG:
            case MESSAGE_TYPE_SYNC:
                buffer.position(buffer.position() + msgSize); //skip this message
                continue;
            default:
                if (msgSize == 0 && msgType == 0) {
                    // This is an error (corrupt file): likely the file is filled with zeros from this point on.
                    // Not much we can do except to ensure that we make progress and don't spam the error console.
                } else {
                    buffer.position(buffer.position() + msgSize);
                    errors.add(new FormatErrorException(pos, "Unknown message type: " + msgType));
                }
                continue;
            }
            int sizeParsed = (int) (position() - pos - HDRLEN);
            if (sizeParsed != msgSize) {
                errors.add(new FormatErrorException(pos, "Message size mismatch, parsed: " + sizeParsed + ", msg size: " + msgSize));
                buffer.position(buffer.position() + msgSize - sizeParsed);
            }
           // System.out.println(msg.toString());
            /********************************/
           // if(counter <= 500) {
            //    writer.write(msg.toString());
            //   writer.write("\r\n");
            //    counter++;
          //  }
            return msg;//到这里读完

        }
    }
    public static boolean deleteFile(File dirFile) {
        // 如果dir对应的文件不存在，则退出
        if (!dirFile.exists()) {
            return false;
        }
         if (dirFile.isFile()) {
             return dirFile.delete();
         }else
             {
                 for (File file : dirFile.listFiles()) {
                     deleteFile(file); }
             }
             return dirFile.delete();
    }

    public Object readfile(String filepath) throws FileNotFoundException, IOException {
        try {

            File file = new File(filepath);
            if (!file.isDirectory()) {
              //  System.out.println("文件");
              //  System.out.println("path=" + file.getPath());
               // System.out.println("absolutepath=" + file.getAbsolutePath());
              //  System.out.println("name=" + file.getName());
            return file;
            } else if (file.isDirectory()) {
             //   System.out.println("文件夹");
                String[] filelist = file.list();
                for (int i = 0; i < filelist.length; i++) {
                    File readfile = new File(filepath + "\\" + filelist[i]);
                    if (!readfile.isDirectory()) {
                        System.out.println("path=" + readfile.getPath());
                        System.out.println("absolutepath="
                                + readfile.getAbsolutePath());
                        System.out.println("name=" + readfile.getName());

                    } else if (readfile.isDirectory()) {
                        readfile(filepath + "\\" + filelist[i]);
                    }
                }

            }

        } catch (FileNotFoundException e) {
            System.out.println("readfile()   Exception:" + e.getMessage());
        }
        return true;
    }

    public static List<String> getAllFile(String directoryPath,boolean isAddDirectory) {
        List<String> list = new ArrayList<String>();
        File baseFile = new File(directoryPath);
        if (baseFile.isFile() || !baseFile.exists()) {
            return list;
        }
        File[] files = baseFile.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                if(isAddDirectory){
                    list.add(file.getAbsolutePath());
                }
                list.addAll(getAllFile(file.getAbsolutePath(),isAddDirectory));
            } else {
                list.add(file.getAbsolutePath());
            }
        }
        return list;
    }
    private static JFileChooser showCustomDialog(Frame owner, Component parentComponent) {
        // 创建一个模态对话框
        final JDialog dialog = new JDialog(owner, "Ulog数据导出器 魅影版", true);
        //设置对话框的宽高
        dialog.setSize(500, 400);
        // 设置对话框大小不可改变
        dialog.setResizable(false);
        //设置对话框相对显示的位置
        dialog.setLocationRelativeTo(parentComponent);
        // 创建一个标签显示消息内容
        JLabel messageLabel = new JLabel("完成后会有提示,时间较长，请耐心等待");
        JLabel messageLabel3 = new JLabel("可以多选或者单选文件，生成同名文件夹保存数据");
        JLabel messageLabel2 = new JLabel("Powered by Di Weicheng");
        //创建一个按钮用于关闭对话框
        JButton okBtn = new JButton("选择文件");
        okBtn.addActionListener(new ActionListener() {
                                    @Override
                                    public void actionPerformed(ActionEvent e) {
                                        //关闭对话框
                                        dialog.dispose();
                                    }
                                });
        // 创建对话框的内容面板, 在面板内可以根据自己的需要添加任何组件并做任意是布局
        JPanel panel = new JPanel();
        // 添加组件到面板



        okBtn.setBounds(200, 280, 100, 40);//按钮的位置大小
        messageLabel.setBounds(50,20,400,50);
        messageLabel2.setBounds(320,320,400,50);
        messageLabel3.setBounds(50,60,400,50);
        panel.setLayout(null);//自定义布局
        panel.add(okBtn);
        panel.add(messageLabel);
        panel.add(messageLabel2);
        panel.add(messageLabel3);

                                    // 设置对话框的内容面板
        dialog.setContentPane(panel);
        // 显示对话框
        dialog.setVisible(true);
        JFileChooser openLogFileChooser = new JFileChooser();
        return openLogFileChooser;
    }
    /*
    Dump each stream of message data records to a CSV file named "topic_N.csv"
    First line of each file is "timestamp,field1,field2,..."
     */

    public static void main(String[] args) throws Exception {
        ULogReader reader = null;
        File[] file3 ;
        JFileChooser openLogFileChooser;
     //   JFrame fr = new JFrame("Ulog数据导出导出器");
      //  fr.setSize(300, 300);
      //  fr.setLocationRelativeTo(null);
      //  fr.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
      //  JButton btn = new JButton("运行");
      //  btn.addActionListener(new ActionListener() {
       //     @Override
       //     public void actionPerformed(ActionEvent e) {
        //        showCustomDialog(fr, fr);
         //   }
      //  });
      //  JPanel panel = new JPanel();
      //  panel.add(btn);

     //   fr.setContentPane(panel);
      //  fr.setVisible(false);
       // JDialog log = new JDialog(fr, "完成后会有提示，点击确定开始",true);
        openLogFileChooser = showCustomDialog(null, null);



        openLogFileChooser.setMultiSelectionEnabled(true);//这里是可以多选


        int mod;
        //mod=JFileChooser.FILES_ONLY  ;//只选择文件
        //mod=JFileChooser.DIRECTORIES_ONLY  ;//只选择目录
        mod=JFileChooser.FILES_AND_DIRECTORIES ;//文件和目录
        openLogFileChooser.setFileSelectionMode(mod);//这里是选择选择信息的模式
        int returnVal  = openLogFileChooser.showDialog(openLogFileChooser, "确认");//打开文件选择框
        file3=openLogFileChooser.getSelectedFiles();

        int counter = 0;
        for(File s:file3) {
            System.out.println(s);
            File p = s;

            String basePath = "D:";
           // openLogFileChooser.setCurrentDirectory(new File(basePath));
        //    File file = new File("D:" + File.separator + "temp.bin");
         //   int returnVal = openLogFileChooser.showDialog(null, "Open");
           // if (returnVal == JFileChooser.APPROVE_OPTION) {
         //       file = openLogFileChooser.getSelectedFile();
                String logFileName = p.getPath();
                basePath = p.getParent();
                reader = new ULogReader(logFileName);
        //    } else {
       //         System.exit(0);
        //    }
            StringBuffer buf = new StringBuffer();
            buf.append(basePath);
            String temp = p.getName();
            temp = temp.replace(".ulg", "");
            buf.append(File.separator);
            buf.append(temp);
            basePath = buf.toString();
            p = new File(basePath);
            if (!p.exists()) {//如果文件夹不存在
                p.mkdir();//创建文件夹

            } else {
                for (File file2 : p.listFiles()) {
                    deleteFile(file2);
                }
                p.mkdir();
            }
            //System.out.println(basePath);
            //System.out.println(file.getName());
            // write all parameters to a gnu Octave data file

            //FileWriter fileWriter = new FileWriter(new File(basePath + File.separator + "parameters.text"));
            //Map<String, Object> tmap = new TreeMap<String, Object>(reader.parameters);
            //Set pSet = tmap.entrySet();
            //for (Object aPSet : pSet) {
            //    Map.Entry param = (Map.Entry) aPSet;
            //    fileWriter.write(String.format("# name: %s\n#type: scalar\n%s\n", param.getKey(), param.getValue()));
            //}
            //fileWriter.close();


            long tStart = System.currentTimeMillis();
            double last_t = 0;
            double last_p = 0;
            Map<String, PrintStream> ostream = new HashMap<String, PrintStream>();
            Map<String, Double> lastTimeStamp = new HashMap<String, Double>();
            double min_dt = 1;
            while (true) {
//            try {
//                Object msg = reader.readMessage();
//                System.out.println(msg);
//            } catch (EOFException e) {
//                break;
//            }
                Map<String, Object> update = new HashMap<String, Object>();
                try {
                    long t = reader.readUpdate(update);
                    double tsec = (double) t / 1e6;
                    if (tsec > (last_p + 1)) {
                        last_p = tsec;
                        System.out.printf("%8.0f\n", tsec);
                    }


                    // keys in Map "update" are fieldnames beginning with the topic name e.g. SENSOR_GYRO_0.someField
                    // Create a printstream for each topic when it is first encountered
                    Set<String> keySet = update.keySet();
                    String stream = keySet.iterator().next().split("\\.")[0];
                    if (!ostream.containsKey(stream)) {
                        System.out.println("creating stream " + stream);
                        PrintStream newStream = new PrintStream(basePath + File.separator + stream + ".csv");
                        ostream.put(stream, newStream);
                        lastTimeStamp.put(stream, tsec);
                        Iterator<String> keys = keySet.iterator();
                        newStream.print("timestamp");
                        while (keys.hasNext()) {
                            String fieldName = keys.next();
                            if (!fieldName.contains("_padding") && fieldName != "timestamp") {
                                newStream.print(',');
                                newStream.print(fieldName);
                            }
                        }
                        newStream.println();
                    }
                    // append this record to output stream
                    PrintStream curStream = ostream.get(stream);
                    // timestamp is always first entry in record
                    curStream.print(t);
                    // for each non-padding field, print value
                    Iterator<String> keys = keySet.iterator();
                    while (keys.hasNext()) {
                        String fieldName = keys.next();
                        if (!fieldName.contains("_padding") && fieldName != "timestamp") {
                            curStream.print(',');
                            curStream.print(update.get(fieldName));
                        }
                    }
//                for (Object field: update.values()) {
//                    curStream.print(',');
//                    curStream.print(field.toString());
//                }
                    curStream.println();
                    // check gyro stream for dropouts
                    if (stream.startsWith("SENSOR_GYRO")) {
                        double dt = tsec - lastTimeStamp.get(stream);
                        double rdt = Math.rint(1000 * dt) / 1000;
                        if ((dt > 0) && (rdt < min_dt)) {
                            min_dt = rdt;
                            System.out.println("rdt: " + rdt);
                        }
                        if (dt > (5 * min_dt)) {
                            System.out.println("gyro dropout: " + lastTimeStamp.get(stream) + ", length: " + dt);
                        }
                        lastTimeStamp.put(stream, tsec);
                    }
                } catch (EOFException e) {
                    break;
                }
            }
            long tEnd = System.currentTimeMillis();
            for (Exception e : reader.getErrors()) {
                e.printStackTrace();
            }
            System.out.println(tEnd - tStart);
            reader.close();


        }
        JOptionPane.showMessageDialog(null, "完成！");
        System.exit(0);
    }

    @Override
    public List<Exception> getErrors() {
        return errors;
    }

    @Override
    public void clearErrors() {
        errors.clear();
    }
}
