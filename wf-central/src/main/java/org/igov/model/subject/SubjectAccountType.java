package org.igov.model.subject;

import javax.persistence.Column;

import org.igov.model.core.AbstractEntity;

@javax.persistence.Entity
public class SubjectAccountType extends AbstractEntity {
    
    @Column
    private String sID;
    
    @Column
    private String sNote;

    public String getsNote() {
        return sNote;
    }

    public void setsNote(String sNote) {
        this.sNote = sNote;
    }

    public String getsID() {
	return sID;
    }

    public void setsID(String sID) {
	this.sID = sID;
    }

}
