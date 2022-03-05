package edu.ncsu.edm.graphgenerator;

import com.opencsv.bean.CsvBindByName;

public class CodeState {

    @CsvBindByName(column = "CodeStateID", required = true)
    private String codeStateId;

    @CsvBindByName(column = "Code", required = false)
    private String code;

    public String getCodeStateId() { 
        return this.codeStateId; 
    };

    public String getCode() {
        return this.code;
    }

    public void setCodeStateId(String codeStateId) {
        this.codeStateId = codeStateId;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
