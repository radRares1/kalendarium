package com.api.service;

import com.api.entities.*;
import com.api.entities.BusinessService;
import com.api.input.*;
import com.api.mapper.*;
import com.api.output.BusinessServiceJSON;
import com.api.output.UserJSON;
import com.api.repository.*;
import com.crypto.Crypt;
import com.crypto.PasswordHash;
import com.email.EmailTemplateService;
import com.email.Template;
import com.internationalization.EmailMessages;
import com.internationalization.Messages;
import com.resources.AppResources;
import com.util.enums.HTTPCustomStatus;
import com.util.enums.Language;
import com.util.exceptions.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.resources.AppResources.ENCRYPTION_KEY;

@Service
public class BusinessServiceService {
    private BusinessRepository businessRepository;
    private UserRepository userRepository;
    private ServiceDetailRepository serviceDetailRepository;
    private BusinessServiceRepository businessServiceRepository;
    private BusinessUsersRepository businessUsersRepository;
    private BusinessUnitRepository businessUnitRepository;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private static final Logger LOG = LoggerFactory.getLogger(BusinessServiceService.class);
    private final EmailTemplateService emailTemplateService;



    @Autowired
    public BusinessServiceService(BusinessRepository businessRepository,
                                  UserRepository userRepository,
                                  ServiceDetailRepository serviceDetailRepository,
                                  BusinessServiceRepository businessServiceRepository,
                                  BusinessUsersRepository businessUsersRepository,
                                  BusinessUnitRepository businessUnitRepository, EmailTemplateService emailTemplateService) {
        this.businessRepository = businessRepository;
        this.userRepository = userRepository;
        this.serviceDetailRepository = serviceDetailRepository;
        this.businessServiceRepository = businessServiceRepository;
        this.businessUsersRepository = businessUsersRepository;
        this.businessUnitRepository = businessUnitRepository;
        this.emailTemplateService = emailTemplateService;
    }

    @Transactional
    public void save(String email, BusinessServiceInput businessServiceInput, Language language) throws GeneralSecurityException {

        String businessCode = businessServiceInput.getBusinessCode();
        businessCode = Crypt.decrypt(businessCode, ENCRYPTION_KEY.value());

        Business business = businessRepository.findByBusinessCode(businessCode).orElseThrow(
                () -> new ApiException(Messages.get("BUSINESS.SERVICE.NOT.EXIST", language), HTTPCustomStatus.UNAUTHORIZED)
        );

        ServiceDetail serviceDetail = ServiceDetailMapper.inputToServiceDetail(businessServiceInput);

        ServiceDetail savedServiceDetail = serviceDetailRepository.save(serviceDetail);

        BusinessService businessService = BusinessServiceMapper.inputToBusinessService(businessServiceInput);
        businessService.setBusiness(business);
        businessService.setServiceDetail(serviceDetail);
        businessService.setBusinessServiceCode(getComplexUUID());

        businessServiceRepository.save(businessService);
    }

    private String getComplexUUID() {
        return UUID.randomUUID().toString() + UUID.randomUUID().toString();
    }

    @Transactional
    public void update(String code, BusinessServiceUpdateInput businessServiceInput, Language language) throws GeneralSecurityException {
        code = Crypt.decrypt(code, ENCRYPTION_KEY.value());

        BusinessService businessService = businessServiceRepository.findByBusinessServiceCode(code).orElseThrow(
                () -> new ApiException(Messages.get("BUSINESS.SERVICE.NOT.EXIST", language), HTTPCustomStatus.UNAUTHORIZED)
        );

        ServiceDetail newServiceDetail = ServiceDetailMapper.updateInputToServiceDetail(businessServiceInput);
        ServiceDetail serviceDetail = businessService.getServiceDetail();
        serviceDetail.setDuration(newServiceDetail.getDuration());

        if (newServiceDetail.getDay() != null)
            serviceDetail.setDay(newServiceDetail.getDay());
        if (newServiceDetail.getEndTime() != null)
            serviceDetail.setEndTime(newServiceDetail.getEndTime());
        if (newServiceDetail.getStartTime() != null)
            serviceDetail.setStartTime(newServiceDetail.getStartTime());

        serviceDetailRepository.save(serviceDetail);

        businessService.setName(businessServiceInput.getName());
        businessService.setServiceDetail(serviceDetail);

        businessServiceRepository.save(businessService);
    }

    @Transactional
    public List<BusinessServiceJSON> load(String code, Language language) throws GeneralSecurityException {
        code = Crypt.decrypt(code, ENCRYPTION_KEY.value());

        Business business = businessRepository.findByBusinessCode(code).orElseThrow(
                () -> new ApiException(Messages.get("BUSINESS.SERVICE.NOT.EXIST", language), HTTPCustomStatus.UNAUTHORIZED)
        );

        List<BusinessService> businessServices = businessServiceRepository.findAllByBusiness(business);
        List<BusinessServiceJSON> businessServiceJSONS = new ArrayList<>();
        for (BusinessService businessService : businessServices) {
            businessServiceJSONS.add(makeBusinessServiceJson(businessService));
        }

        return businessServiceJSONS;
    }

    private BusinessServiceJSON makeBusinessServiceJson(BusinessService business) throws GeneralSecurityException {
        BusinessServiceJSON result = BusinessServiceMapper.businessServiceToOutput(business);
        return result;
    }


    @Transactional
    public void delete(String businessServiceCode, Language language) throws GeneralSecurityException {

        businessServiceCode = Crypt.decrypt(businessServiceCode, ENCRYPTION_KEY.value());

        businessServiceRepository.deleteByBusinessServiceCode(businessServiceCode);
    }


    @Transactional
    public Set<LocalTime> getSchedule(BusinessServiceScheduleInput businessServiceInput, Language language) throws GeneralSecurityException {

        String businessServiceCode = Crypt.decrypt(businessServiceInput.getBusinessCode(), ENCRYPTION_KEY.value());

        BusinessService businessService = businessServiceRepository.findByBusinessServiceCode(businessServiceCode).orElseThrow(() -> new ApiException(Messages.get("BUSINESS.SERVICE.NOT.EXIST", language), HTTPCustomStatus.UNAUTHORIZED));

        ServiceDetail serviceDetail = businessService.getServiceDetail();

        LocalDate date = LocalDate.parse(businessServiceInput.getDate(), DATE_FORMATTER);

        List<BusinessUnit> businessUnits = businessService.getUnitsPerformingService();

        Set<LocalTime> availableTime = new HashSet<>();

        for (BusinessUnit businessUnit : businessUnits) {
            availableTime.addAll(getScheduleForBusinessUnit(businessUnit, serviceDetail, date));
        }

        return availableTime.stream().sorted().collect(Collectors.toCollection(LinkedHashSet::new));
    }


    private List<LocalTime> getScheduleForBusinessUnit(BusinessUnit businessUnit, ServiceDetail serviceDetail, LocalDate date) {
        List<Appointment> appointments = businessUnit.getAppointments();
        appointments = appointments.stream()
                .filter(appointment -> date.equals(appointment.getDate()))
                .collect(Collectors.toList());

        List<LocalTime> times = new ArrayList<>();

        LocalTime currentTime = serviceDetail.getStartTime();
        LocalTime endTime = serviceDetail.getEndTime();
        while (currentTime.isBefore(endTime)) {
            times.add(currentTime);
            currentTime = currentTime.plusMinutes(serviceDetail.getDuration());
        }

        Set<LocalTime> timeSet = new HashSet<>();

        for (Appointment appointment : appointments) {
            LocalTime startTime = appointment.getStartTime();
            endTime = appointment.getEndTime();
            for (LocalTime time : times) {
                if ((time.isAfter(startTime) || time.equals(startTime)) && time.isBefore(endTime)) {
                    timeSet.add(time);
                }
                LocalTime time1 = time.plusMinutes(serviceDetail.getDuration());
                if ((time1.isAfter(startTime) || time1.equals(startTime)) && time1.isBefore(endTime)) {
                    timeSet.add(time);
                }
            }
        }

        times.removeAll(timeSet);
        return times;
    }

    @Transactional
    public void addEmployee(String email, EmployeeInput employeeInput, Language language) throws GeneralSecurityException {

        Optional<User> loggedUser = userRepository.findByEmail(email);

        User user = saveUser(employeeInput, email, language);
        BusinessUsers businessUsers = saveBusinessUser(loggedUser.get(), user);
        BusinessUnit businessUnit = saveBusinessUnit(businessUsers.getBusiness(), employeeInput, email, user);

        businessUsers.getBusiness().addBusinessUnit(businessUnit);
        for (String businessServiceCode : employeeInput.getBusinessServiceCodes()) {
            Optional<BusinessService> foundBusinessService = businessServiceRepository.findByBusinessServiceCode(businessServiceCode);
            foundBusinessService.ifPresent(businessService -> businessService.addBusinessUnit(businessUnit));
        }

    }

    private BusinessUnit saveBusinessUnit(Business business, EmployeeInput employeeInput, String email, User user) throws GeneralSecurityException {
        Optional<BusinessUnit> foundBusinessUnit = businessUnitRepository.findByUser(user);
        if (!foundBusinessUnit.isPresent()) {
            BusinessUnit businessUnit = new BusinessUnit();
            businessUnit.setName(employeeInput.getFirstName() + " " + employeeInput.getLastName());
            businessUnit.setBusiness(business);
            businessUnit.setBusinessOwnerEmail(email);
            businessUnit.setBusinessUnitCode(getComplexUUID());
            businessUnit.setUser(user);
            businessUnitRepository.save(businessUnit);
        }
        return businessUnitRepository.findByUser(user).get();
    }

    private BusinessUsers saveBusinessUser(User loggedUser, User foundUser) {
        List<BusinessUsers> businessUsers = loggedUser.getBusinessUsers();
        Business business = businessUsers.size() > 0 ? loggedUser.getBusinessUsers().get(0).getBusiness() : null;
        BusinessUsers businessUsersNew = new BusinessUsers(business, foundUser);
        businessUsersRepository.save(businessUsersNew);
        return businessUsersNew;
    }

    private User saveUser(EmployeeInput employeeInput, String email, Language language) {
        Optional<User> foundUser = userRepository.findByEmail(employeeInput.getEmail());
        if (!foundUser.isPresent()) {
            User user = UserMapper.employeeToUser(employeeInput);
            String uuid = String.valueOf(UUID.randomUUID());
            LocalDateTime now = LocalDateTime.now();
            user.setUserKey(uuid);
            user.setCreatedDate(now);
            userRepository.save(user);


            // send email for user to set password
            try {
                Map<String, Object> templateKeysAndValues = new HashMap<>();
                String url = AppResources.ACCOUNT_PASSWORD_URL.value() + "?key=" + Crypt.encrypt(user.getUserKey(), ENCRYPTION_KEY.value());
                templateKeysAndValues.put("fullName", user.getFullName());
                templateKeysAndValues.put("confirmationLink", url);
                emailTemplateService.send(templateKeysAndValues, user.getEmail(), EmailMessages.get("create.account.subject", language.getLocale()), Template.CREATE_ACCOUNT, language.getLocale());
            } catch (GeneralSecurityException e) {
                throw new ApiException(Messages.get("ENCRYPTION.FAILED", language), HTTPCustomStatus.UNAUTHORIZED);
            }


            return user;
        }
        return foundUser.get();
    }

    @Transactional
    public void updateEmployeePassword(String userKey, String password, Language language) throws GeneralSecurityException {
        userKey  = Crypt.decrypt(userKey, ENCRYPTION_KEY.value());

        User user = userRepository.findByUserKey(userKey).orElseThrow(
                () -> new ApiException(Messages.get("USER.NOT.EXIST", language), HTTPCustomStatus.UNAUTHORIZED)
        );

        String hashedPassword = PasswordHash.encode(password);

        user.setPassword(hashedPassword);
        userRepository.save(user);
    }

    @Transactional
    public List<UserJSON> getAllEmployeesForBusiness(String email) {

        Optional<User> loggedUser = userRepository.findByEmail(email);
        List<BusinessUsers> businessUserss = loggedUser.get().getBusinessUsers();
        Business business = businessUserss.size() > 0 ? loggedUser.get().getBusinessUsers().get(0).getBusiness() : null;

        return businessUsersRepository.findAll().stream()
                .filter(businessUsers -> businessUsers.getBusiness().equals(business))
                .map(BusinessUsers::getUser)
                .map(UserMapper::userToJson)
                .collect(Collectors.toList());
    }

    @Transactional
    public void addBusinessUnit(BusinessUnitServiceInput businessServiceInput, Language language) throws GeneralSecurityException {

        String businessServiceCode = Crypt.decrypt(businessServiceInput.getBusinessServiceCode(), ENCRYPTION_KEY.value());
        String businessUnitCode = Crypt.decrypt(businessServiceInput.getBusinessUnitCode(), ENCRYPTION_KEY.value());


        BusinessService businessService = this.businessServiceRepository.findByBusinessServiceCode(businessServiceCode)
                .orElseThrow(() -> new ApiException(Messages.get("BUSINESS.SERVICE.NOT.EXIST", language), HTTPCustomStatus.UNAUTHORIZED)
                );
        BusinessUnit businessUnit = this.businessUnitRepository.findByBusinessUnitCode(businessUnitCode)
                .orElseThrow(() -> new ApiException(Messages.get("BUSINESS.UNIT.NOT.EXIST", language), HTTPCustomStatus.UNAUTHORIZED));


        businessService.addBusinessUnit(businessUnit);

        this.businessServiceRepository.save(businessService);
    }
}

