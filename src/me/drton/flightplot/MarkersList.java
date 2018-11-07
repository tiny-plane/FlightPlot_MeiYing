package me.drton.flightplot;

import me.drton.flightplot.Marker;

import java.util.ArrayList;

/**
 * Created by ton on 29.09.15.
 */
public class MarkersList extends ArrayList<Marker> implements PlotItem {//画点
    private final String title;

    public MarkersList(String title) {
        this.title = title;
    }

    @Override
    public String getTitle() {//获取标题
        return title;
    }

    public String getFullTitle(String processorTitle) {
        return processorTitle + (title.isEmpty() ? "" : (":" + title));
    }

    public void addMarker(double time, String label) {//加点，需要时间和标签
        add(new Marker(time, label));//就是一些坐标
    }
}
