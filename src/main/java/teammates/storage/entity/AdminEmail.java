package teammates.storage.entity;

import java.util.Date;
import java.util.List;

import com.google.appengine.api.datastore.Text;

import com.googlecode.objectify.annotation.Entity;
import com.googlecode.objectify.annotation.Id;
import com.googlecode.objectify.annotation.Index;
import com.googlecode.objectify.annotation.Unindex;

/**
 * Represents emails composed by Admin.
 */
@Entity
@Index
public class AdminEmail extends BaseEntity {

    @Id
    private Long emailId;

    //this stores the address string eg."example1@test.com,example2@test.com...."
    private List<String> addressReceiver;

    //this stores the blobkey string of the email list file uploaded to Google Cloud Storage
    private List<String> groupReceiver;

    private String subject;

    //For draft emails,this is null. For sent emails, this is not null;
    private Date sendDate;

    private Date createDate;

    @Unindex
    private Text content;

    private boolean isInTrashBin;

    /**
     * Instantiates a new AdminEmail.
     * @param subject
     *          email subject
     * @param content
     *          html email content
     */
    public AdminEmail(List<String> addressReceiver, List<String> groupReceiver, String subject,
                      Text content, Date sendDate) {
        this.emailId = null;
        this.addressReceiver = addressReceiver;
        this.groupReceiver = groupReceiver;
        this.subject = subject;
        this.content = content;
        this.sendDate = sendDate;
        this.createDate = new Date();
        this.isInTrashBin = false;
    }

    public void setAddressReceiver(List<String> receiver) {
        this.addressReceiver = receiver;
    }

    public void setGroupReceiver(List<String> receiver) {
        this.groupReceiver = receiver;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setContent(Text content) {
        this.content = content;
    }

    public void setIsInTrashBin(boolean isInTrashBin) {
        this.isInTrashBin = isInTrashBin;
    }

    public void setSendDate(Date sendDate) {
        this.sendDate = sendDate;
    }

    public String getEmailId() {
        return this.emailId.toString();
    }

    public List<String> getAddressReceiver() {
        return this.addressReceiver;
    }

    public List<String> getGroupReceiver() {
        return this.groupReceiver;
    }

    public String getSubject() {
        return this.subject;
    }

    public Date getSendDate() {
        return this.sendDate;
    }

    public Text getContent() {
        return this.content;
    }

    public boolean getIsInTrashBin() {
        return this.isInTrashBin;
    }

    public Date getCreateDate() {
        return this.createDate;
    }
}
