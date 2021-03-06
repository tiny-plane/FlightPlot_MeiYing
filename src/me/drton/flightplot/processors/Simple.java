package me.drton.flightplot.processors;

import me.drton.flightplot.processors.tools.LowPassFilter;

import java.util.HashMap;
import java.util.Map;

/**
 * User: ton Date: 15.06.13 Time: 12:04
 */
public class Simple extends PlotProcessor {
    protected String[] param_Fields;
    protected double param_Scale;
    protected double param_Offset;
    protected double param_Delay;
    protected LowPassFilter[] lowPassFilters;

    @Override
    public Map<String, Object> getDefaultParameters() {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("Fields", "ATT.Pitch ATT.Roll");
        params.put("Delay", 0.0);
        params.put("LPF", 0.0);
        params.put("Scale", 1.0);
        params.put("Offset", 0.0);
        return params;
    }

    @Override
    public void init() {
        param_Fields = ((String) parameters.get("Fields")).split(WHITESPACE_RE);
        param_Scale = (Double) parameters.get("Scale");
        param_Offset = (Double) parameters.get("Offset");
        param_Delay = (Double) parameters.get("Delay");
        lowPassFilters = new LowPassFilter[param_Fields.length];
        for (int i = 0; i < param_Fields.length; i++) {
            LowPassFilter lowPassFilter = new LowPassFilter();
            lowPassFilter.setF((Double) parameters.get("LPF"));//传入时间
            lowPassFilters[i] = lowPassFilter;
        }
        for (String field : param_Fields) {
            addSeries(field);
        }
    }

    protected double preProcessValue(int idx, double time, double in) {
        return in;
    }

    protected double postProcessValue(int idx, double time, double in) {
        return in;
    }


    @Override
    public void process(double time, Map<String, Object> update) {//这是吧所有设置为simple的都更新一下，如果使用滚轮，实际上就是改变了比例，如果拖拽就是改变了偏移，这样刷新一次需要很多时间

        for (int i = 0; i < param_Fields.length; i++) {
            String field = param_Fields[i];
            Object v = update.get(field);
           // System.out.println(v.toString());
            if (v != null && v instanceof Number) {
                double out = preProcessValue(i, time, ((Number) v).doubleValue());
                if (Double.isNaN(out)) {//如果是第一个点，就不需要低通滤波，直接添加
                    addPoint(i, time, Double.NaN);
                } else {//之后的点都进过一下低通滤波再画出来
                    out = lowPassFilters[i].getOutput(time, out);
                    out = postProcessValue(i, time, out);
                    addPoint(i, time + param_Delay, out * param_Scale + param_Offset);
                }
            }
        }
    }
}
