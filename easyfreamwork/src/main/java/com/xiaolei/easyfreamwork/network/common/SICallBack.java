package com.xiaolei.easyfreamwork.network.common;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.support.v4.app.FragmentActivity;

import com.xiaolei.easyfreamwork.Config.Config;
import com.xiaolei.easyfreamwork.common.InstanceObjCatch;
import com.xiaolei.easyfreamwork.network.regist.Regist;
import com.xiaolei.easyfreamwork.network.regist.RegisteTable;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.lang.reflect.Method;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import rx.Observer;

import static com.xiaolei.easyfreamwork.application.ApplicationBreage.networkErrorTimes;

/**
 * Created by xiaolei on 2017/7/9.
 */

public abstract class SICallBack<T> implements Callback<T>, Observer<T>
{
    public SoftReference<Context> context;
    private IUnifiedFailEvent failEvent;
    
    public SICallBack(Context context)
    {
        this.context = new SoftReference<>(context);
        failEvent = InstanceObjCatch.getInstance().get(Config.UnifiedFailEventKlass);
    }

    public SICallBack(Fragment fragment)
    {
        this(fragment.getActivity());
    }

    public SICallBack(android.support.v4.app.Fragment fragment)
    {
        this(fragment.getActivity());
    }

    public abstract void onSuccess(T result) throws Exception;

    public void onFail(Throwable t)
    {
        if (checkActivityFinish()) return;
        if (context.get() != null)
        {
            networkErrorTimes ++ ;
            t.printStackTrace();
            UnifiedFailEvent(t);
        }
    }

    public abstract void onFinally();

    @Override
    public void onResponse(Call<T> call, Response<T> response)
    {
        if (checkActivityFinish()) return;
        try
        {
            if (response.isSuccessful())
            {
                onNext(response.body());
            } else
            {
                onFail(new IOException(response.code() + ""));
            }
        } catch (Exception e)
        {
            onFail(new IOException(e));
        } finally
        {
            onFinally();
        }
    }

    @Override
    public void onFailure(Call<T> call, Throwable t)
    {
        if (checkActivityFinish()) return;
        try
        {
            onFail(t);
        } finally
        {
            onFinally();
        }
    }

    @Override
    public void onCompleted()
    {
        if (checkActivityFinish()) return;
        onFinally();
    }

    @Override
    public void onError(Throwable e)
    {
        if (checkActivityFinish()) return;
        try
        {
            onFail(e);
        } finally
        {
            
            onFinally();
        }
    }

    @Override
    public void onNext(T bodyBean)
    {
        if (checkActivityFinish()) return;
        try
        {
            Class<? extends Regist> regist = RegisteTable.getInstance().getRegistValue(bodyBean);
            if (regist != null)
            {
                Regist registObj = RegisteTable.getInstance().getRegistObj(regist);
                if (registObj != null)
                {
                    String callback = registObj.filter(bodyBean);
                    if (callback != null && !callback.isEmpty())
                    {
                        Method method = registObj.getMethod(callback);
                        if (method != null)
                        {
                            Class paramTypes[] = method.getParameterTypes();
                            Object objs[] = new Object[paramTypes.length];
                            for (int i = 0; i < paramTypes.length; i++)
                            {
                                Class paramtype = paramTypes[i];
                                if (paramtype == Context.class)
                                {
                                    objs[i] = context.get();
                                } else if (paramtype.isInstance(bodyBean))
                                {
                                    objs[i] = bodyBean;
                                } else
                                {
                                    objs[i] = null;
                                }
                            }
                            if (!method.isAccessible())
                            {
                                method.setAccessible(true);
                            }
                            method.invoke(registObj, objs);
                            return;
                        }
                    }
                }
            }
            onSuccess(bodyBean);
        } catch (Exception e)
        {
            e.printStackTrace();
        } finally
        {
            networkErrorTimes = 0;
        }
    }

    /**
     * 检查界面是否关闭了
     *
     * @return
     */
    private boolean checkActivityFinish()
    {
        Context mContext = context.get();
        if (mContext != null)
        {
            if (FragmentActivity.class.isInstance(mContext))
            {
                FragmentActivity activity = (FragmentActivity) mContext;
                return activity.isFinishing();
            }

            if (Activity.class.isInstance(mContext))
            {
                Activity activity = (Activity) mContext;
                return activity.isFinishing();
            }
            return false;
        } else
        {
            return true;
        }
    }

    /**
     * 统一的错误处理方式
     */
    private void UnifiedFailEvent(Throwable e)
    {
        if(failEvent != null)
        {
            failEvent.onFail(this,e,context.get());
        }
    }
}
