/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.mobile.activities.registerpatient;

import org.openmrs.mobile.api.RestApi;
import org.openmrs.mobile.api.RestServiceBuilder;
import org.openmrs.mobile.api.retrofit.PatientApi;
import org.openmrs.mobile.dao.PatientDAO;
import org.openmrs.mobile.listeners.retrofit.DefaultResponseCallbackListener;
import org.openmrs.mobile.models.Module;
import org.openmrs.mobile.models.Patient;
import org.openmrs.mobile.models.Results;
import org.openmrs.mobile.utilities.ApplicationConstants;
import org.openmrs.mobile.utilities.ModuleUtils;
import org.openmrs.mobile.utilities.NetworkUtils;
import org.openmrs.mobile.utilities.PatientComparator;
import org.openmrs.mobile.utilities.StringUtils;
import org.openmrs.mobile.utilities.ToastUtil;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterPatientPresenter implements RegisterPatientContract.Presenter {

    private final RegisterPatientContract.View mRegisterPatientView;

    private Patient mPatient;
    private List<String> mCountries;

    public RegisterPatientPresenter(RegisterPatientContract.View mRegisterPatientView, List<String> countries) {
        this.mRegisterPatientView = mRegisterPatientView;
        this.mRegisterPatientView.setPresenter(this);
        this.mCountries = countries;
    }
    
    @Override
    public void start(){}

    @Override
    public void confirm(Patient patient) {
        if(validate(patient)) {
            mRegisterPatientView.setProgressBarVisibility(true);
            mRegisterPatientView.hideSoftKeys();
            findSimilarPatients(patient);
        }
        else {
            mRegisterPatientView.scrollToTop();
        }
    }

    @Override
    public void finishRegisterActivity() {
        mRegisterPatientView.finishRegisterActivity();
    }

    private boolean validate(Patient patient) {

        boolean ferr=false, lerr=false, doberr=false, gerr=false, adderr=false, countryerr=false;
        mRegisterPatientView.setErrorsVisibility(ferr, lerr, doberr, gerr, adderr, countryerr);

        // Validate names
        if(StringUtils.isBlank(patient.getPerson().getName().getGivenName())) {
            ferr=true;
        }
        if(StringUtils.isBlank(patient.getPerson().getName().getFamilyName())) {
            lerr=true;
        }

        // Validate date of birth
        if(StringUtils.isBlank(patient.getPerson().getBirthdate())) {
            doberr = true;
        }

        // Validate address
        if(StringUtils.isBlank(patient.getPerson().getAddress().getAddress1())
                && StringUtils.isBlank(patient.getPerson().getAddress().getAddress2())
                && StringUtils.isBlank(patient.getPerson().getAddress().getCityVillage())
                && StringUtils.isBlank(patient.getPerson().getAddress().getStateProvince())
                && StringUtils.isBlank(patient.getPerson().getAddress().getCountry())
                && StringUtils.isBlank(patient.getPerson().getAddress().getPostalCode())) {
            adderr=true;
        }

        if (!StringUtils.isBlank(patient.getPerson().getAddress().getCountry()) && !mCountries.contains(patient.getPerson().getAddress().getCountry())) {
            countryerr = true;
        }

        // Validate gender
        if (StringUtils.isBlank(patient.getPerson().getGender())) {
            gerr=true;
        }

        boolean result = !ferr && !lerr && !doberr && !adderr && !countryerr && !gerr;
        if (result) {
            mPatient = patient;
            return true;
        }
        else {
            mRegisterPatientView.setErrorsVisibility(ferr, lerr, doberr, adderr, countryerr, gerr);
            return false;
        }
    }

    @Override
    public void registerPatient() {
        new PatientApi().registerPatient(mPatient, new DefaultResponseCallbackListener() {
            @Override
            public void onResponse() {
                mRegisterPatientView.startPatientDashbordActivity(mPatient);
                mRegisterPatientView.finishRegisterActivity();
            }

            @Override
            public void onErrorResponse() {
                mRegisterPatientView.setProgressBarVisibility(false);
            }
        });
    }

    public void findSimilarPatients(final Patient patient){
        if (NetworkUtils.isOnline()) {
            RestApi apiService = RestServiceBuilder.createService(RestApi.class);
            Call<Results<Module>> moduleCall = apiService.getModules(ApplicationConstants.API.FULL);
            moduleCall.enqueue(new Callback<Results<Module>>() {
                @Override
                public void onResponse(Call<Results<Module>> call, Response<Results<Module>> response) {
                    if(response.isSuccessful()){
                        if(ModuleUtils.isRegistrationCore1_7orAbove(response.body().getResults())){
                            fetchSimilarPatientsFromServer(patient);
                        } else {
                            fetchSimilarPatientAndCalculateLocally(patient);
                                                    }
                    } else {
                        fetchSimilarPatientAndCalculateLocally(patient);
                    }
                }

                @Override
                public void onFailure(Call<Results<Module>> call, Throwable t) {
                    mRegisterPatientView.setProgressBarVisibility(false);
                    ToastUtil.error(t.getMessage());
                }
            });
        } else {
            List<Patient> similarPatient = new PatientComparator().findSimilarPatient(new PatientDAO().getAllPatients(), patient);
            if(!similarPatient.isEmpty()){
                mRegisterPatientView.showSimilarPatientDialog(similarPatient, patient);
            } else {
                registerPatient();
            }
        }
    }

    private void fetchSimilarPatientAndCalculateLocally(final Patient patient) {
        RestApi apiService = RestServiceBuilder.createService(RestApi.class);
        Call<Results<Patient>> call = apiService.getPatients(patient.getPerson().getName().getGivenName(), ApplicationConstants.API.FULL);
        call.enqueue(new Callback<Results<Patient>>() {
            @Override
            public void onResponse(Call<Results<Patient>> call, Response<Results<Patient>> response) {
                if(response.isSuccessful()){
                    List<Patient> patientList = response.body().getResults();
                    if(!patientList.isEmpty()){
                        List<Patient> similarPatient = new PatientComparator().findSimilarPatient(patientList, patient);
                        if (!similarPatient.isEmpty()) {
                            mRegisterPatientView.showSimilarPatientDialog(similarPatient, patient);
                            mRegisterPatientView.showUpgradeRegistrationModuleInfo();
                        }
                    } else {
                        registerPatient();
                    }
                } else {
                    mRegisterPatientView.setProgressBarVisibility(false);
                    ToastUtil.error(response.message());
                }
            }

            @Override
            public void onFailure(Call<Results<Patient>> call, Throwable t) {
                mRegisterPatientView.setProgressBarVisibility(false);
                ToastUtil.error(t.getMessage());
            }
        });
    }

    private void fetchSimilarPatientsFromServer(final Patient patient) {
        RestApi apiService = RestServiceBuilder.createService(RestApi.class);
        Call<Results<Patient>> call = apiService.getSimilarPatients(patient.toMap());
        call.enqueue(new Callback<Results<Patient>>() {
            @Override
            public void onResponse(Call<Results<Patient>> call, Response<Results<Patient>> response) {
                if(response.isSuccessful()){
                    List<Patient> similarPatients = response.body().getResults();
                    if(!similarPatients.isEmpty()){
                        mRegisterPatientView.showSimilarPatientDialog(similarPatients, patient);
                    } else {
                        registerPatient();
                    }
                } else {
                    mRegisterPatientView.setProgressBarVisibility(false);
                    ToastUtil.error(response.message());
                }
            }

            @Override
            public void onFailure(Call<Results<Patient>> call, Throwable t) {
                mRegisterPatientView.setProgressBarVisibility(false);
                ToastUtil.error(t.getMessage());
            }
        });
    }

}
