package edu.ncsu.lab.ast_tagger;

import com.opencsv.bean.CsvBindByName;

import java.util.List;

public class ProgramFileCommit {
    @CsvBindByName(column = "commitid", required = true)
    private final String commitId;

    @CsvBindByName(column = "repo_name", required = true)
    private final String repoName;

    @CsvBindByName(column = "assignment", required = true)
    private final String lab;

    @CsvBindByName(column = "file", required = true)
    private final String fileName;

    @CsvBindByName(column = "data")
    private final String code;

    private List<String> imports;

    public ProgramFileCommit() {
        this("Undefined", "Undefined", "Undefined", "Undefined", "Undefined", List.of());
    }

    public ProgramFileCommit(String code) {
        this("Test", "Test", "Test", "Test", code, List.of());
    }

    public ProgramFileCommit(String commitId, String repoName, String lab, String fileName,
                             String code, List<String> imports) {
        this.commitId = commitId;
        this.repoName = repoName;
        this.lab = lab;
        this.fileName = fileName;
        this.code = code;
        this.imports = imports;
    }

    public String getCommitId() {
        return commitId;
    }

    public String getRepoName() {
        return repoName;
    }

    public String getLab() {
        return lab;
    }

    public String getFileName() {
        return fileName;
    }

    public String getCode() {
        return code;
    }

    public List<String> getImports() {
        return imports;
    }

    public void setImports(List<String> imports) {
        this.imports = imports;
    }

}
