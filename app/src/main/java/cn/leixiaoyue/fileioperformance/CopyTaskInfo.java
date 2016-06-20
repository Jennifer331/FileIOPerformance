package cn.leixiaoyue.fileioperformance;

/**
 * Created by 80119424 on 2016/4/21.
 */
public class CopyTaskInfo extends TaskInfo {
    public long duration;
    public long fileSize;

    public CopyTaskInfo(int type, long duration, long fileSize){
        this.type = type;
        this.duration = duration;
        this.fileSize = fileSize;
    }

    @Override
    public String toString() {
        return FileHelper.mapMethodName(type) + "\n用时：" + duration + "ms      文件大小：" + fileSize / 1024.0 / 1024.0 + "Mb";
    }
}
