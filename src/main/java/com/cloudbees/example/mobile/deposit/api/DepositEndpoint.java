package com.cloudbees.example.mobile.deposit.api;

/**
 * Created by kmadel on 6/12/15.
 */

import com.cloudbees.example.mobile.deposit.api.model.Deposit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import java.math.BigDecimal;


@Component
@Path("/account/deposit")
public class DepositEndpoint {

    @Value("${version}")
    private String version;

    @GET
    public Deposit getDepositAccount() {

        Deposit depositAccount = new Deposit();
        depositAccount.setAccountNumber("XXXXXXX9876");
        depositAccount.setBalance(new BigDecimal(8760.85));
        depositAccount.setVersion(version);

        return depositAccount;
    }

}