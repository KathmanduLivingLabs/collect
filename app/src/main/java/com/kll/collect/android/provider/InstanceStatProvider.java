package com.kll.collect.android.provider;

/**
 * Created by pujan on 8/23/15.
 */
public class InstanceStatProvider {
    private Integer completed;
    private String formName;
    private String formID;
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

    public Integer getAllSent()
    {

        return (sent-no_attachment);
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

    public String getFormID() {
        return formID;
    }

    public void setFormID(String formID) {
        this.formID = formID;
    }
}