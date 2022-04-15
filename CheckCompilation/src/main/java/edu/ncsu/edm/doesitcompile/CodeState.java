package edu.ncsu.edm.doesitcompile;

import com.opencsv.bean.CsvBindByName;

import java.util.List;

public class CodeState {

    @CsvBindByName(column = "CodeStateID", required = true)
    private String codeStateId;

    @CsvBindByName(column = "Code")
    private String code;

    private List<String> imports;

    public CodeState() {
        this("Undefined", "Undefined");
    }

    public CodeState(String codeStateId, String code) {
        this(codeStateId, code, List.of());
    }

    public CodeState(String codeStateId, String code, List<String> imports) {
        this.codeStateId = codeStateId;
        this.code = code;
        this.imports = imports;
    }

    public String getCodeStateId() {
        return this.codeStateId;
    }

    ;

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