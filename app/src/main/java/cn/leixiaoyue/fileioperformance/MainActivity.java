package cn.leixiaoyue.fileioperformance;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.ScatterChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.ScatterData;
import com.github.mikephil.charting.data.ScatterDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.github.mikephil.charting.interfaces.datasets.IScatterDataSet;
import com.github.mikephil.charting.utils.ColorTemplate;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.leixiaoyue.directorypicker.DirectoryPicker;

/**
 * Created by 80119424 on 2016/4/18.
 */
public class MainActivity extends Activity implements Statistics.ChartListener{
    private static final String TAG = "MainActivity";
    private static final int GET_FILE = 0;
    private static final int SET_SRC_DIR = 1;
    private static final int SET_DEST_DIR = 2;

    private Button mSrcBtn, mDestBtn;
    private TextView mSrcTxt, mDestTxt, mConsole;
    private File mCurrentFile = null;
    private File mDestFile = null;
    private String mSrcFolder = null;
    private String mDestFolder = null;
    private String mFilename;

    private LineChart mChart;
//    private ScatterChart mChart;
    ArrayList<ILineDataSet> mDataSets = new ArrayList<ILineDataSet>();
//    ArrayList<IScatterDataSet> mDataSets = new ArrayList<IScatterDataSet>();
    ArrayList<String> mXVals = new ArrayList<String>();

    private final int[] mColors = {Color.BLUE, Color.MAGENTA, Color.GRAY, Color.GREEN, Color.BLACK, Color.YELLOW, Color.RED};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_layout);

        FileHelper.initStatistics(this, this);
        initView();
        initListener();
        initChart();
    }

    private void initView(){
        mSrcBtn = (Button)findViewById(R.id.src);
        mDestBtn = (Button)findViewById(R.id.dest);
        mConsole = (TextView)findViewById(R.id.console);
        mConsole.setMovementMethod(new ScrollingMovementMethod());
        mSrcTxt = (TextView)findViewById(R.id.showsrc);
        mDestTxt = (TextView)findViewById(R.id.showdest);
        mChart = (LineChart)findViewById(R.id.chart);
//        mChart = (ScatterChart) findViewById(R.id.chart);
    }

    private void initListener(){
        mSrcBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
//                intent.setType("*/*");
//                intent.addCategory(Intent.CATEGORY_OPENABLE);
//
//                startActivityForResult(Intent.createChooser(intent, "请选择要拷贝的文件"), GET_FILE);

                Intent intent = new Intent(MainActivity.this, DirectoryPicker.class);
                startActivityForResult(intent, SET_SRC_DIR);
            }
        });

        mDestBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DirectoryPicker.class);
                startActivityForResult(intent, SET_DEST_DIR);
            }
        });
    }

    private void initChart(){
        Legend l = mChart.getLegend();
        l.setPosition(Legend.LegendPosition.ABOVE_CHART_CENTER);
        mChart.setDrawGridBackground(false);
        mChart.setDescription("");
        mChart.setDrawBorders(false);

//        mChart.getAxisLeft().setDrawAxisLine(false);
//        mChart.getAxisLeft().setDrawGridLines(false);
//        mChart.getAxisRight().setDrawAxisLine(false);
//        mChart.getAxisRight().setDrawGridLines(false);
//        mChart.getXAxis().setDrawAxisLine(false);
//        mChart.getXAxis().setDrawGridLines(false);

        // enable touch gestures
        mChart.setTouchEnabled(true);

        // enable scaling and dragging
        mChart.setDragEnabled(true);
        mChart.setScaleEnabled(true);

        // if disabled, scaling can be done on x- and y-axis separately
        mChart.setPinchZoom(false);
        initAxises();
    }

    private void initAxises(){
        YAxis leftAxis = mChart.getAxisLeft();
        leftAxis.removeAllLimitLines(); // reset all limit lines to avoid overlapping lines
//        leftAxis.setAxisMaxValue(100f);
//        leftAxis.setAxisMinValue(0f);
        leftAxis.enableGridDashedLine(10f, 10f, 0f);
        leftAxis.setDrawZeroLine(false);

        mChart.getAxisRight().setEnabled(false);

        XAxis topAxis = mChart.getXAxis();
        topAxis.removeAllLimitLines();
//        topAxis.setAxisMaxValue(10000000f);
//        topAxis.setAxisMinValue(0f);
        topAxis.enableGridDashedLine(10f, 10f, 0f);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        switch(requestCode){
            case GET_FILE:
                Uri uri = data.getData();
                String path = FileHelper.getRealPathFromUri(this, uri);
                mSrcTxt.setText(path);
                try {
                    mCurrentFile = new File(path);
                    mFilename = path.substring(path.lastIndexOf("/"), path.length());
                }catch(Exception e){
                    Log.e(TAG, "[onActivityResult]", e);
                }
                break;
            case SET_SRC_DIR:
                mSrcFolder = data.getStringExtra(DirectoryPicker.CHOSEN_DIRECTORY);
                mSrcTxt.setText(mSrcFolder);
                mCurrentFile = new File(mSrcFolder);
                break;
            case SET_DEST_DIR:
                mDestFolder = data.getStringExtra(DirectoryPicker.CHOSEN_DIRECTORY);
//                String destPath = Environment.getExternalStorageDirectory() + "/test/test";
                mDestTxt.setText(mDestFolder);
                mDestFile = new File(mDestFolder);
                break;
        }
    }

    public void startCopySingle(View view){
        try {
            long startTime = System.currentTimeMillis();
            TaskInfo info = null;
            info = FileHelper.ioBufferedStream(mCurrentFile, mDestFile);
            mConsole.append(info + " \n");
            info = FileHelper.ioUnBufferedStream(mCurrentFile, mDestFile);
            mConsole.append(info + " \n");
            info = FileHelper.transferTo(mCurrentFile, mDestFile);
            mConsole.append(info + " \n");
            info = FileHelper.transferFrom(mCurrentFile, mDestFile);
            mConsole.append(info + " \n");
            info = FileHelper.channelMappedBuffer(mCurrentFile, mDestFile);
            mConsole.append(info + " \n");
            info = FileHelper.directBuffer(mCurrentFile, mDestFile);
            mConsole.append(info + " \n");
            info = FileHelper.nonDirectBuffer(mCurrentFile, mDestFile);
            mConsole.append(info + " \n");

//            long endTime = System.currentTimeMillis();
//            mConsole.append("File size:" + mCurrentFile.length() / 1024.0 / 1024.0+ "Mb \n");
//            mConsole.append("Total time:" + (endTime - startTime) + "ms \n");
        }catch(Exception e){
            Log.e(TAG, "[startCopy]", e);
        }
    }

    public void startCopyFiles(View view) throws IOException{
        File directory;
        for(final File file : mCurrentFile.listFiles()){
            TaskInfo info = null;
            File dest;
            switch(view.getId()){
                case R.id.io_buffered_stream:
                    directory = new File(mDestFile + "/" + "0");
                    if(!directory.exists()){
                        directory.mkdirs();
                    }
                    dest = new File(mDestFile + "/" + "0" + "/" + file.getName());
                    Log.v(TAG, "dest:" + dest);
                    info = FileHelper.ioBufferedStream(file, dest);
                    break;
                case R.id.io_unbuffered_stream:
                    directory = new File(mDestFile + "/" + "1");
                    if(!directory.exists()){
                        directory.mkdirs();
                    }
                    dest = new File(mDestFile + "/" + "1" + "/" + file.getName());
                    info = FileHelper.ioUnBufferedStream(file, dest);
                    break;
                case R.id.channel_transfer_to:
                    directory = new File(mDestFile + "/" + "2");
                    if(!directory.exists()){
                        directory.mkdirs();
                    }
                    dest = new File(mDestFile + "/" + "2" + "/" + file.getName());
                    info = FileHelper.transferTo(file, dest);
                    break;
                case R.id.channel_transfer_from:
                    directory = new File(mDestFile + "/" + "3");
                    if(!directory.exists()){
                        directory.mkdirs();
                    }
                    dest = new File(mDestFile + "/" + "3" + "/" + file.getName());
                    info = FileHelper.transferFrom(file, dest);
                    break;
                case R.id.channel_mapped_buffer:
                    directory = new File(mDestFile + "/" + "4");
                    if(!directory.exists()){
                        directory.mkdirs();
                    }
                    dest = new File(mDestFile + "/" + "4" + "/" + file.getName());
                    info = FileHelper.channelMappedBuffer(file, dest);
                    break;
                case R.id.channel_direct_buffer:
                    directory = new File(mDestFile + "/" + "5");
                    if(!directory.exists()){
                        directory.mkdirs();
                    }
                    dest = new File(mDestFile + "/" + "5" + "/" + file.getName());
                    info = FileHelper.directBuffer(file, dest);
                    break;
                case R.id.channel_non_direct_buffer:
                    directory = new File(mDestFile + "/" + "6");
                    if(!directory.exists()){
                        directory.mkdirs();
                    }
                    dest = new File(mDestFile + "/" + "6" + "/" + file.getName());
                    info = FileHelper.nonDirectBuffer(file, dest);
                    break;
            }
            mConsole.append(info + "\n");
        }
    }

    public void clearConsole(View view){
        mConsole.setText("");
    }

    public void clearDestFolder(View view){
        for(File file : mDestFile.listFiles()){
            file.delete();
        }
    }

    @Override
    public void refreshData(Map<Integer, List<CopyTaskInfo>> datas) {
        mDataSets = parseDatas(datas);
        LineData data = new LineData(mXVals, mDataSets);
//        ScatterData data = new ScatterData(mXVals, mDataSets);
        mChart.setData(data);
        mChart.invalidate();
    }

    private ArrayList<ILineDataSet> parseDatas(Map<Integer, List<CopyTaskInfo>> datas){
//    private ArrayList<IScatterDataSet> parseDatas(Map<Integer, List<CopyTaskInfo>> datas){
        ArrayList<ILineDataSet> results = new ArrayList<>();
//        ArrayList<IScatterDataSet> results = new ArrayList<>();
        Set<Integer> dataSets = datas.keySet();
        for(int set : dataSets){
            String setName = FileHelper.mapMethodName(set);
            List<Entry> values = new ArrayList<>();
            List<CopyTaskInfo> setDatas = datas.get(set);
            Collections.sort(setDatas, new Comparator<CopyTaskInfo>() {
                @Override
                public int compare(CopyTaskInfo lhs, CopyTaskInfo rhs) {
                    return (int)(lhs.fileSize - rhs.fileSize);
                }
            });
            mXVals.clear();
            for(CopyTaskInfo info : datas.get(set)){
//                int size = (int)(info.fileSize / 1024.0);//Kb
                int size = (int)(info.fileSize);
                values.add(new Entry(info.duration, size));
                mXVals.add(size + "");
            }

            LineDataSet d = new LineDataSet(values, setName);
            d.setLineWidth(2.5f);
            d.setCircleRadius(4f);

            int color = mColors[set % 7];
            d.setColor(color);
            d.setCircleColor(color);
            results.add(d);
//            ScatterDataSet scatterSet = new ScatterDataSet(values, setName);
//            scatterSet.setScatterShape(ScatterChart.ScatterShape.CIRCLE);
//            int color = mColors[set % 7];
//            scatterSet.setScatterShapeHoleColor(color);
//            scatterSet.setScatterShapeHoleRadius(5f);
//            scatterSet.setColor(color);
//            results.add(scatterSet);
        }

        return results;
    }
}
