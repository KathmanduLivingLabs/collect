package com.kll.collect.android.provider;

/**
 * Created by pujan on 8/23/15.
 */
public class InstanceStatProvider {
    private Integer completed;
    private String formName;
    private Integer no_attachment;
    private Integer not_sent;
    private Integer sent;

    public InstanceStatProvider()
    {
    }

    public Integer getCompleted()
    {
        return completed;
    }

    public String getFormName()
    {
        return formName;
    }

    public Integer getNo_attachment()
    {
        return no_attachment;
    }

    public Integer getNot_sent()
    {
        return not_sent;
    }

    public Integer getSent()
    {
        return sent;
    }

    public void setCompleted(Integer integer)
    {
        completed = integer;
    }

    public void setFormName(String s)
    {
        formName = s;
    }

    public void setNo_attachment(Integer integer)
    {
        no_attachment = integer;
    }

    public void setNot_sent(Integer integer)
    {
        not_sent = integer;
    }

    public void setSent(Integer integer)
    {
        sent = integer;
    }
}
