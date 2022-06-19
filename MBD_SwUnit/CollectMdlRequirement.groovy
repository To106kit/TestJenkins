#! groovy

/**
 * << CollectMdlRequirement.groovy >>
 *
 * This job is for running "CollectMdlRequirement". It consists of two stages, 
 * "Read yaml" and "Execute" stage. "Read yaml" stage reads the configuration file defined in YAML format
 * on the Container. "Execute" stage downloads the necessary files from the SCM, determines the units
 * to be executed by CollectMdlRequirement, and executes it on them.
 *
 * @version 1.2.0
 * 
 * @param String  params.netDriveUsespace  Container folder path created to exchange data between jobs.
 * @param String  params.executeLabel      Define the node label in which is executed "Read yaml" stage.
 * 
 * @author DNJP
 */

// Read Jenkins Shared Library
@Library('SPIFWLibrary@R2021.B.04')

import cictframework._common.CICTFrameworkException

def yamlConfig
def processName = 'mdl_prepare'
def toolName = 'CollectMdlRequirement'
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
                        yamlConfig = configuration.read("${params.netDriveUsespace}\\Config\\${processName}\\${toolName}\\${toolName}.yaml")
                        // yamlConfig = mbdCommonProcessing.readConfig(processName, toolName, params.netDriveUsespace)
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
                    // label params.executeLabel
                    label yamlConfig.labelName
                    customWorkspace custom_workspace
                }
            }
            steps {
                script {
                    try {
                        prepareWorkspace(yamlConfig, processName, toolName, params.netDriveUsespace)
                        
                        try{
                        bat '''
                            net use Z: https://webdav.geniie.net/gsp4inci/gsp4inci/ H85!=69t /u:tatsuya.yamada.j6u@jpgr.denso.com  /persistent:yes
                        '''
                        }catch (Exception e){
                            //設定済みのため何もしない.
                        }

                        // Re-read yaml (to replace env ver of Jenkins)
                        yamlConfig = configuration.read("${params.netDriveUsespace}\\Config\\${processName}\\${toolName}\\${toolName}.yaml")

                        // Replace place holder "<UNIT_NAME>" with Jenkins job folder name
                        def jFolderNameList = env.JOB_NAME.split('/')

                        jFolderName = jFolderNameList[jFolderNameList.size()-3]
                        appFolderName = jFolderNameList[1]

                        Map unitPlaceHolder = [:]
                        unitPlaceHolder['<UNIT_NAME>'] = jFolderName
                        Map appPlaceHolder = [:]
                        appPlaceHolder['<APP_NAME>'] = appFolderName

                        preyamlConfig = searchfiles.replacePlaceHolders(yamlConfig, unitPlaceHolder)
                        yamlConfig = searchfiles.replacePlaceHolders(preyamlConfig, appPlaceHolder)


                        // git変更開発  
                        // checkout from GIT
                        if (yamlConfig.scm.git != null) {
                            if (yamlConfig.scm.git.containsKey('slproj')) {
                                downloadForGIT("${env.WORKSPACE}\\${yamlConfig.scm.git.slproj.name}",
                                                        yamlConfig.scm.git.slproj.url,
                                                        yamlConfig.scm.git.slproj.credentials,
                                                        yamlConfig.scm.git.slproj.branches)
                            } else {
                                // checkout mbd common
                                downloadForGIT("${env.WORKSPACE}\\${yamlConfig.scm.git.common.name}",
                                                        yamlConfig.scm.git.common.url,
                                                        yamlConfig.scm.git.common.credentials,
                                                        yamlConfig.scm.git.common.branches)

                                // checkout system
                                downloadForGIT("${env.WORKSPACE}\\${yamlConfig.scm.git.system.name}",
                                                        yamlConfig.scm.git.system.url,
                                                        yamlConfig.scm.git.system.credentials,
                                                        yamlConfig.scm.git.system.branches)
                            }
                        }


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
                    } catch (Exception e) {
                        println e.message
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
                                // When using SimulinkProject, this process needs to MATLAB.
                                // git変更開発
                                if (newYamlConfig.scm.git.containsKey('slproj')) {
                                    def parameters = [:]
                                    parameters["modelName"] = searchResult.basename
                                    parameters["projFile"] = newYamlConfig.scm.git.slproj.projFile
                                    parameters["fileServerTop"] = "${params.netDriveUsespace}\\Result\\${processName}\\${toolName}"
                                    parameters["matlab"] = newYamlConfig.scm.git.slproj.matlab
                                    
                                    collectMdlRequirement(parameters)
                                } else {
                                    // Prepare sldd, common modules, then upload to NetDrive
                                    swunitMain.prepareModelRequirements(searchResult.basename,
                                                                        searchResult.fileDir,
                                                                        "${env.WORKSPACE}\\${newYamlConfig.scm.git.common.name}",  
                                                                        "${env.WORKSPACE}\\${newYamlConfig.scm.git.system.name}",
                                                                        "${env.WORKSPACE}\\${newYamlConfig.scm.git.system.name}\\${newYamlConfig.scm.git.system.systemCommonFolder}",
                                                                        params.netDriveUsespace,
                                                                        processName,
                                                                        toolName)
                                }
                            } catch (CICTFrameworkException e) {
                                if (e.id.equals('0005600003')) {   // MATLAB returned nonzero exit
                                    Map errinfo = e.errinfo
                                    def quotientRetVal = (int)(errinfo.matlabRetVal / 100)

                                    if (quotientRetVal == 1) {   // Warning => UNSTABLE
                                        printMessage('1005600001', errinfo.matlabRetVal.toString())
                                        currentBuild.result = 'UNSTABLE'
                                    } else if (quotientRetVal == 2) {   // Error => FAILURE
                                        printMessage(e.id, errinfo.matlabRetVal.toString())
                                        printMessage('0009100001', searchResult.fileName)
                                        errorModels << searchResult.fileName
                                    } else {
                                        printMessage(e.id, errinfo.matlabRetVal.toString())
                                        printMessage('0005600004')
                                        printMessage('0009100001', searchResult.fileName)
                                        errorModels << searchResult.fileName
                                    }
                                } else {   // other error (e.g. validate parameters)
                                    printMessage(e)
                                    printMessage('0009100001', searchResult.fileName)
                                    errorModels << searchResult.fileName
                                }
                            } catch (Exception e) {
                                println e.message
                                printMessage('0009100001', searchResult.fileName)
                                errorModels << searchResult.fileName
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
    // mbdCommonProcessing.downloadSourceFolders(config, tool, containerPath)
    // mbdCommonProcessing.downloadConfigurationFileFromServer(config, tool)
    // mbdCommonProcessing.downloadPreJobResult(config, tool, containerPath)
}

// Git対応
void downloadForGIT(String checkoutPath, String url, String credentials, String branches) {
    // if(revision == null){
    //     revision = "HEAD"
    // }
    
    dir (checkoutPath) {
        checkout([$class: 'GitSCM', 
        branches: [[name: "${branches}"]], 
        doGenerateSubmoduleConfigurations: false, 
        extensions: [], 
        submoduleCfg: [], 
        userRemoteConfigs: [
            [credentialsId: "${credentials}", 
            url: "${url}"
            ]
        ]
    ])
    }
}


