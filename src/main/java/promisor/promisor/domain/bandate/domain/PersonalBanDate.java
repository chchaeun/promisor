package promisor.promisor.domain.bandate.domain;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicInsert;
import promisor.promisor.domain.member.domain.Member;
import promisor.promisor.domain.model.BaseEntity;

import javax.persistence.*;


import java.util.Date;

import static javax.persistence.FetchType.*;

@Entity
@Getter
@Table(name = "personal_ban_date")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@DynamicInsert
public class PersonalBanDate extends BaseEntity {

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    private Date date;

    @Column(name="date_status", length = 10, columnDefinition = "varchar(10) default 'IMPOSSIBLE'")
    private String dateStatus;

    public PersonalBanDate(Member member, Date date){
        super("ACTIVE");
        this.member = member;
        this.date = date;
        this.dateStatus = "IMPOSSIBLE";
    }

    public void editPBDStatus(String status) {
        this.dateStatus = status;
    }
}
