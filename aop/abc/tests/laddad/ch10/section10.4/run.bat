del banking\*.class
del logging\*.class
del sample\module\*.class
del sample\principal\*.class

call ajc banking\*.java auth\*.java logging\*.java sample\module\*.java sample\principal\*.java
@echo on

call java -Djava.security.auth.login.config=sample_jaas.config banking.Test