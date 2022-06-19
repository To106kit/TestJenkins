#! groovy

/**
 * << MdlCheck.groovy >>
 *
 * This job is for running "MdlCheck" of mdl_design process. It consists of two stages, 
 * "Read yaml" and "Execute" stage. "Read yaml" stage reads the configuration file defined in YAML format
 * on the Container. "Execute" stage downloads the necessary files from the Container, determines the units
 * to be executed by MdlCheck, and executes it on them. Then, upload the artifacts to the specified
 * location in the configuration file.
 *
 * @version 1.2.0
 * 
 * @param String  params.netDriveUsespace  Container folder path created to exchange data between jobs.
 * @param String  params.executeLabel      Define the node label in which is executed "Read yaml" stage.
 * 
 * @author DNJP
 */

// Read SharedLibrary
@Library('SPIFWLibrary@R2021.B.04')

import cictframework._common.CICTFrameworkException

def yamlConfig
def processName = 'mdl_design'
def toolName = 'MdlCheck'
def custom_workspace = 'C:\\jenkins\\dnec_tmp'

pipeline {
    agent none
    stages {
        stage('Read yaml') {
            agent {
                label params.executeLabel
            }
            steps {
                script {
                    try {
                        // Initialize Message
                        initialMessage()

                        // Read config
                        yamlConfig = mbdCommonProcessing.readConfig(processName, toolName, params.netDriveUsespace)
                    } catch (CICTFrameworkException e) {
                        printMessage(e)
                        currentBuild.result = 'FAILURE'
                        throw e
                    }
                }
            }
        }
        stage('Execute') {
            agent {
                node {
                    label yamlConfig.labelName
                    customWorkspace custom_workspace
                }
            }
            steps {
                script {
                    def searchResults
                    yamlConfig = configuration.read("${params.netDriveUsespace}\\Config\\${processName}\\${toolName}\\${toolName}.yaml")   // to replace env ver of Jenkins

                    try {
                        // Prepare workspace
                        prepareWorkspace(yamlConfig, processName, toolName, params.netDriveUsespace)

                        // Replace place holder "<UNIT_NAME>" with Jenkins job folder name
                        def jFolderName = env.JOB_NAME.split('/')
                        jFolderName = jFolderName[jFolderName.size()-3]
                        
                        Map unitPlaceHolder = [:]
                        unitPlaceHolder['<UNIT_NAME>'] = jFolderName
                        yamlConfig = searchfiles.replacePlaceHolders(yamlConfig, unitPlaceHolder)

                        // Search model files
                        searchResults = mbdCommonProcessing.searchModelFiles(yamlConfig)

                        // Display list of models
                        def searchMsgs = []
                        searchMsgs << '+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++'
                        searchMsgs << '[CI/CT] [Information] The list of models to be executed is as follows:'
                        searchResults.fileName.each { ele ->
                            searchMsgs << ele
                        }
                        searchMsgs << '+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++'
                        println searchMsgs.join('\n')
                    } catch (CICTFrameworkException e) {
                        printMessage(e)
                        currentBuild.result = 'FAILURE'
                        throw e
                    }

                    // Process to execute tool
                    try {
                        def errorModels = []
                        for (searchResult in searchResults) {
                            Map modelPlaceHolders = [:]
                            modelPlaceHolders['<MODEL_FILE>'] = searchResult.fileName
                            modelPlaceHolders['<MODEL_DIR>'] = searchResult.fileDir
                            modelPlaceHolders['<MODEL_BASE_NAME>'] = searchResult.basename

                            def newYamlConfig = searchfiles.replacePlaceHolders(yamlConfig, modelPlaceHolders)
                            try {
                                // Execute MdlCheck
                                mdlCheck(newYamlConfig.parameters)
                            } catch (CICTFrameworkException e) {
                                if (e.id.equals('0005600003')) {   // MATLAB returned nonzero exit
                                    Map errinfo = e.errinfo
                                    def quotientRetVal = (int)(errinfo.matlabRetVal / 100)

                                    if (quotientRetVal == 1) {   // Warning => UNSTABLE
                                        printMessage('1005600001', errinfo.matlabRetVal.toString())
                                        currentBuild.result = 'UNSTABLE'
                                    } else if (quotientRetVal == 2) {   // Error => FAILURE
                                        printMessage(e.id, errinfo.matlabRetVal.toString())
                                        printMessage('0008500001', searchResult.fileName)
                                        errorModels << searchResult.fileName
                                    } else {
                                        printMessage(e.id, errinfo.matlabRetVal.toString())
                                        printMessage('0005600004')
                                        printMessage('0008500001', searchResult.fileName)
                                        errorModels << searchResult.fileName
                                    }
                                } else {   // other error (e.g. validate parameters)
                                    printMessage(e)
                                    printMessage('0008500001', searchResult.fileName)
                                    errorModels << searchResult.fileName
                                }
                            } catch (Exception e) {
                                println e.message
                                printMessage('0008500001', searchResult.fileName)
                                errorModels << searchResult.fileName
                            } finally {
                                fileOperations([
                                    folderCreateOperation(folderPath:"Result\\${searchResult.basename}\\${searchResult.basename}_MdlCheck_Result"),
                                    folderCopyOperation(sourceFolderPath:"${searchResult.fileDir}\\${searchResult.basename}_MdlCheck_Result", destinationFolderPath:"Result\\${searchResult.basename}\\${searchResult.basename}_MdlCheck_Result")
                                ])
                            }
                        }

                        // Display execution results
                        def resultMsgs = []
                        resultMsgs << '+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++'
                        resultMsgs << '[CI/CT] [Information] The results of execution for each models are as follows:'
                        searchResults.fileName.each { ele ->
                            def resultMsg = errorModels.contains(ele) ? 'SKIP: ' + ele : '    : ' + ele
                            resultMsgs << resultMsg
                        }
                        resultMsgs << '+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++'
                        println resultMsgs.join('\n')

                        if (!errorModels.isEmpty()) {
                            currentBuild.result = searchResults.fileName == errorModels ? 'FAILURE' : 'UNSTABLE'
                        }
                    } catch (Exception e) {
                        currentBuild.result = 'FAILURE'
                        throw e
                    } finally {
                        // Post-processing
                        mbdCommonProcessing.deletePreJobResultFoldersOnWorkspace(yamlConfig)
                        mbdCommonProcessing.publishHTMLReport(yamlConfig, '**\\report_*.html', '')
                        mbdCommonProcessing.uploadResultsByArchiveArtifacts(yamlConfig, currentBuild.currentResult)
                        mbdCommonProcessing.uploadResultsToNetDrive(yamlConfig, currentBuild.currentResult, processName, toolName, params.netDriveUsespace)
                    }
                }
            }
        }
    }
}

/********************************************************************************
 * The following functions are private functions.
 ********************************************************************************/

void prepareWorkspace(Map config, String process, String tool, String containerPath) {
    mbdCommonProcessing.initializeWorkspace()
    mbdCommonProcessing.downloadConfigFolder(process, tool, containerPath)
    mbdCommonProcessing.downloadSourceFolders(config, tool, containerPath)
    //mbdCommonProcessing.downloadConfigurationFileFromServer(config, tool)
    mbdCommonProcessing.downloadPreJobResult(config, tool, containerPath)
}
