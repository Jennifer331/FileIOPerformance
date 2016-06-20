package cn.leixiaoyue.fileioperformance;

import android.content.Context;
import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Little Happy on 2016/4/21.
 */
public class Statistics {
    public static final String TAG = "Statistics";

    private static volatile Statistics INSTANCE = null;
    //    private Map<Integer, TaskInfo> mDatas = new HashMap<Integer, TaskInfo>();
    private Map<Integer, List<CopyTaskInfo>> mDatas = new HashMap<>();
    private Handler mMainHandler;
    private static ChartListener mListener;

    public static final int SOLUTION_ZERO = 0;
    public static final int SOLUTION_ONE = 1;
    public static final int SOLUTION_TWO = 2;
    public static final int SOLUTION_THREE = 3;
    public static final int SOLUTION_FOUR = 4;
    public static final int SOLUTION_FIVE = 5;
    public static final int SOLUTION_SIX = 6;
    public static final int SOLUTION_SEVEN = 7;

    public static final int REFRESH_CHART = 0;

    public interface ChartListener {
        void refreshData(Map<Integer, List<CopyTaskInfo>> datas);
    }

    private Statistics(Context context, ChartListener listener) {
        mListener = listener;
        mMainHandler = new Handler(context.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case REFRESH_CHART:
                        mListener.refreshData(mDatas);
                        break;
                }
            }
        };
    }

    public static Statistics getInstance(Context context, ChartListener listener) {
        if (null == INSTANCE) {
            return new Statistics(context, listener);
        }
        mListener = listener;
        return INSTANCE;
    }

    public void addData(CopyTaskInfo task) {
        List<CopyTaskInfo> infos = mDatas.get(task.type);
        if (null == infos) {
            infos = new ArrayList<>();
            mDatas.put(task.type, infos);
        }
        infos.add(task);
//        mMainHandler.sendEmptyMessage(REFRESH_CHART);
        mMainHandler.removeMessages(REFRESH_CHART);
        mMainHandler.sendEmptyMessageDelayed(REFRESH_CHART, 500);
    }
}
