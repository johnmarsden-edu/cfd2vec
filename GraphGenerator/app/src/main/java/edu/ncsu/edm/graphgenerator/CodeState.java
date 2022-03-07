package edu.ncsu.edm.graphgenerator;

import com.opencsv.bean.CsvBindByName;

public class CodeState {

    @CsvBindByName(column = "CodeStateID", required = true)
    private String codeStateId;

    @CsvBindByName(column = "Code")
    private String code;

    public CodeState(String codeStateId, String code) {
        this.codeStateId = codeStateId;
        this.code = code;
    }

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
