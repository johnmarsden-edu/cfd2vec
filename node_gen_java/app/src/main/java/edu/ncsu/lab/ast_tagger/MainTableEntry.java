package edu.ncsu.lab.ast_tagger;

import com.opencsv.bean.CsvBindByName;

public class MainTableEntry {
    @CsvBindByName(column = "SubjectID", required = true)
    private String subjectId;

    @CsvBindByName(column = "CodeStateID", required = true)
    private String codeStateId;

    @CsvBindByName(column = "Compile.Result")
    private String compileResult;

    @CsvBindByName(column = "EventType", required = true)
    private String eventType;

    public String getEventType() {
        return eventType;
    }

    public String getCodeStateId() {
        return codeStateId;
    }

    public String getCompileResult() {
        return compileResult;
    }

    public String getSubjectId() {
        return subjectId;
    }
}
