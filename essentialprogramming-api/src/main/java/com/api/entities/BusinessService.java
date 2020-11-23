package com.api.entities;

import lombok.*;

import javax.persistence.*;
import java.util.List;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity(name = "businessservice")
@Table(name = "businessservice")
public class BusinessService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "businessserviceid", nullable = false, unique = true)
    private int id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servicedetailid")
    private ServiceDetail serviceDetail;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "businessid")
    private Business business;

    @OneToMany(mappedBy = "businessservice" ,fetch = FetchType.LAZY)
    private List<Appointment> appointments;

    @OneToMany(mappedBy = "businessservice" ,fetch = FetchType.LAZY)
    private List<Subscription> subscriptions;

}