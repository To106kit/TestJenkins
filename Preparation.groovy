/**
 * プロジェクトを実行する際に使用するデータをネットドライブへ集める。
 * 
 * @version 1.04.00
 * 
 * @param String params.netDrive ネットドライブのパス
 * @param String params.netDriveUsespace 今回のJenkinsBuildで使用するネットドライブのパス
 * @param String params.executeLabel 基本として使用する実行ノードラベルの指定。
 * @param String params.scopeID ScopeID
 * 
 * @return currentBuildクラスオブジェクト
 */

//ライブラリの読み込み
// library 'SPIFWLibrary@R2021.B.00'

//Map&List型 "Preparation.yaml"の内容を取得する変数
def yamlPreparationConfig

pipeline {
    agent {
        label "${params.executeLabel}"
    }
    stages {
            stage('03 mkdir') {
                steps {
                    script {
                        echo "##### 03 mkdir #####"
                    }
                }
            }
        // stage('Init Config') {
        //     steps {
        //         script {
        //             //メッセージ関数の初期化
        //             initialMessage()
                    
        //             //ScopeIDの詳細欄への表示
        //             currentBuild.description = "ScopeID:${params.scopeID}"

        //             echo '[CI/CT] [Information] 01 Download Preparation config'
        //             //"Preparation.yaml"をネットドライブからコピーし、"yamlPreparationConfig"へ読み込む。
        //             dir("${params.netDriveUsespace}") {
        //                 fileOperations([
        //                     folderCopyOperation(sourceFolderPath: '\\Config\\Preparation',
        //                                         destinationFolderPath: "${env.WORKSPACE}")
        //                 ])
        //             }
        //             echo '[CI/CT] [Information] 02 read config.yaml'
        //             try {
        //                 yamlPreparationConfig = readYaml file: "${env.WORKSPACE}\\Preparation.yaml"
        //             } catch (Exception e) {
        //                 printMessage("0001100001")
        //                 currentBuild.result = 'FAILURE'
        //                 //error
        //             }

        //             if(!fileExists("${params.netDrive}\\7zip")){
        //                 //複数のジョブで 7zip-extra でのファイル圧縮・解凍ができるよう、すべてのジョブがアクセスできるフォルダに 7zip-extra をコピーする。
        //                 //Parentジョブでは、7zipコピーできないためPreparationジョブで実施する。 
        //                 fileOperations([
        //                     folderCopyOperation(sourceFolderPath: "${env.WORKSPACE}\\7zip", destinationFolderPath: "${params.netDrive}\\7zip")
        //                 ])
        //             }
        //         }
        //     }
        // }
        // stage('Download source code') {
        //     steps {
        //         echo '[CI/CT] [Information] 01 Source download'
        //         //"Preparation.yaml"で指定されたすべてのソースをダウンロードする。
        //         script {
        //             for(scm in yamlPreparationConfig.scm){
        //                 //Map&List型 データを取得1接続先ごとに取得したものを再定義。
        //                 def scmTmp = scm
        //                 //fromパラメータ(scm種類名)がnullであれば処理をその場で中断する。
        //                 if(scmTmp.from == null) break
        //                 for(info in scmTmp.info){
        //                     //Map&List型 接続URLやリビジョン、認証情報のデータを一つずつ取り出し、再定義。
        //                     def infoTmp = info

        //                     // ---------------------------------------------------------------------------------------------------------
        //                     // info.searchFilePattern が指定されている場合のみ実行
        //                     if (scmTmp.from == "SVN" && infoTmp.containsKey('searchFilePattern')) {
        //                         if (!infoTmp.searchFilePattern) {   // 値が空
        //                             // [memo]
        //                             // 値が空の場合は、info.searchFilePattern未指定時と同じ動作とする
        //                         }else{
        //                             // Jenkins 上での Preparation ジョブへのパスから部品名を取得
        //                             // Preparation ジョブが存在するフォルダが部品名であることが前提
        //                             def jFolderName = env.JOB_NAME.split('/')
        //                             jFolderName = jFolderName[jFolderName.size()-2]

        //                             // searchFilePattern の "<UNIT_NAME>" を部品名で置換
        //                             Map unitPlaceHolder = [:]
        //                             unitPlaceHolder['<UNIT_NAME>'] = jFolderName
        //                             infoTmp.searchFilePattern = searchfiles.replacePlaceHolders(infoTmp.searchFilePattern, unitPlaceHolder)

        //                             // credentials から ユーザ名とパスワードを取得するブロック
        //                             // ※ このブロックの外でユーザ名やパスワードを出力したりしないよう注意
        //                             // Credentials Binding plugin: https://plugins.jenkins.io/credentials-binding/
        //                             List<String> matchList = []
        //                             withCredentials([usernamePassword(credentialsId: infoTmp.credentials, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        //                                 // svn list で info.url 以下の全てのファイル名を取得
        //                                 String svnFiles = bat returnStdout: true, script: "svn list ${infoTmp.url} -R --username $USERNAME --password $PASSWORD"

        //                                 // svn list の結果から searchFilePattern で指定したファイル名が存在するフォルダを探索
        //                                 for (fileName in svnFiles.normalize().split("\n").toList()) {
        //                                     if (fileName == infoTmp.searchFilePattern) {   // infoTmp.url の直下にある場合 ==> ファイルが存在するフォルダへのパスを空としてリストに追加
        //                                         matchList += ['']
        //                                     } else if (fileName.contains('/' + infoTmp.searchFilePattern)) {   // infoTmp.url 以下のどこかのフォルダ内にある場合 ==> ファイルが存在するフォルダへのパスをリストに追加
        //                                         matchList += [fileName.split(infoTmp.searchFilePattern)[0]]
        //                                     }
        //                                 }
        //                             }
        //                             // searchFilePattern で指定したパターンが見つからなかった場合もしくは複数見つかった場合は、infoTmp.url を更新しない。
        //                             if (matchList.size() == 0) {   // 指定したパターンが見つからなかった場合
        //                                 printMessage("0001100002", "Not found `${infoTmp.searchFilePattern}` in ${infoTmp.url}. Source")
        //                                 error 'Look at the red message.'
        //                             } else if (matchList.size() >= 2) {   // 複数見つかった場合
        //                                 printMessage("0001100002", "There was more than one `${infoTmp.searchFilePattern}` in ${infoTmp.url}. Source")
        //                                 error 'Look at the red message.'
        //                             } else {   // １つだけ見つかった場合
        //                                 echo '[CI/CT] [Information] 02 Found `searchFilePattern` in scm.info.url. Updating scm.info.url.'
        //                                 // 探索したフォルダを指定するよう infoTmp.url を上書き
        //                                 if (infoTmp.url[infoTmp.url.length()-1] == '/') {   // url の末尾が '/'
        //                                     infoTmp.url += matchList[0]
        //                                 } else {
        //                                     infoTmp.url += '/' + matchList[0]
        //                                 }
        //                             }
        //                         }
        //                     }
        //                     // ---------------------------------------------------------------------------------------------------------
                            
        //                     //オリジナルのソースコードをWorkSpaceへダウンロードする。
        //                     dir(path: "${env.WORKSPACE}\\${infoTmp.name}"){
        //                         try{
        //                             Download(scmTmp.from, infoTmp.url, infoTmp.credentials, infoTmp.revision, infoTmp.branches, infoTmp.name)
        //                         }catch (Exception e) {
        //                             printMessage("0001100002","${infoTmp.url}")
        //                             currentBuild.result = 'FAILURE'
        //                             //error
        //                         }
        //                     }
        //                 }
        //             }
        //         }
        //     }
        // }
        // stage('zip to netDrive') {
        //     steps {
        //         echo '[CI/CT] [Information] 01 copy to netDrive and zip'
        //         //ワークスペース配下にダウンロードしたソースコードを圧縮し、netDriveへコピーする。
        //         script {
        //             for(scm in yamlPreparationConfig.scm){
        //                 //Map&List型
        //                 def scmTmp = scm
        //                 if(scmTmp.from == null) break
        //                 for(info in scmTmp.info){
        //                     //Map&List型
        //                     def infoTmp = info
        //                     try{
        //                         compressionWith7zip("${params.netDriveUsespace}\\${infoTmp.name}.zip", "${env.WORKSPACE}\\${infoTmp.name}")
        //                     }catch (Exception e){
        //                         printMessage("0001100003","${params.netDriveUsespace}\\${infoTmp.name}.zip")
        //                         currentBuild.result = 'FAILURE'
        //                         //error
        //                     }
        //                 }
        //             }
        //         }
        //     }
        // }
        // stage('Delete workspace') {
        //     steps {
        //         echo '[CI/CT] [Information] 01 delete folder'
        //         //ワークスペース内のデータを削除する。
        //         script {
        //             def count = 0;
        //             while(count < 5) {
        //                 try {
        //                     dir(path: "${env.WORKSPACE}"){deleteDir()}
        //                     if(!fileExists("${env.WORKSPACE}")){
        //                         break
        //                     }
        //                 }catch (Exception e) {
        //                     printMessage("1001100002")
        //                     count = count + 1
        //                     sleep 5
        //                 }
        //             }
        //         }
        //     }
        // }
    }
}

/**
 * fromパラメータにより、ダウンロード方法のスイッチを行う。
 * 
 * @version 1.0
 * 
 * @param String from ダウンロードに使用するApplication
 * @param String url ダウンロードする指定のURL
 * @param String credentials Jenkinsに登録した、ダウンロードする際の認証情報
 * @param String revision ダウンロードするURLのリビジョン指定
 * @param String branches ダウンロードするブランチ名(Gitのみ)
 * @param String name ダウンロードするフォルダの名前
 * 
 * @return なし
 */
void Download(String from, String url, String credentials, String revision, String branches, String name){
    if(from == "SVN"){
        DownloadForSVN(url, credentials, revision, name);
    }else if(from == "Git"){
        DownloadForGit(url, credentials, branches, name);
    }else if(from == "NetFolder"){
        DownloadForNetFolder(url);
    }
    
}

/**
 * SVNから指定のURLのダウンロードを行う。
 * 
 * @version 1.0
 * 
 * @param String url ダウンロードする指定のURL
 * @param String credentials Jenkinsに登録した、ダウンロードする際の認証情報
 * @param String revision ダウンロードするURLのリビジョン指定
 * @param String name ダウンロードするフォルダの名前
 * 
 * @return なし
 */
void DownloadForSVN(String url, String credentials, String revision, String name){
    //もし"revision"がnullであった場合、"HEAD"としてデフォルト値を設定する。
    if(revision == null){
        printMessage("1001100001")
        revision = "HEAD"
    }
    //revision = null ?: "HEAD"
    
    def scmVars = checkout([$class: 'SubversionSCM',
        additionalcredentials: [],
        excludedCommitMessages: '',
        excludedRegions: '',
        excludedRevprop: '',
        excludedUsers: '',
        filterChangelog: false,
        ignoreDirPropChanges: false,
        includedRegions: '',
        locations: [
                [cancelProcessOnExternalsFail: true,
                    credentialsId: "${credentials}",
                    depthOption: 'infinity',
                    ignoreExternalsOption: true,
                    local: '.',
                    remote: "${url}@${revision}"]
        ],
        quietOperation: true, workspaceUpdater: [$class: 'UpdateUpdater']]
    )

    fileOperations([
        fileCreateOperation(fileName: "${params.netDriveUsespace}\\Result\\${name}_${scmVars.SVN_REVISION}.txt", fileContent: "")
    ])
}

/**
 * Gitから指定のURLのダウンロードを行う。
 * 
 * @version 1.0
 * 
 * @param String url ダウンロードする指定のURL
 * @param String credentials Jenkinsに登録した、ダウンロードする際の認証情報
 * @param String branches ダウンロードするブランチ名
 * @param String name ダウンロードするフォルダの名前
 * 
 * @return なし
 */
void DownloadForGit(String url, String credentials, String branches, String name){
    def scmVars = checkout([$class: 'GitSCM', 
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
    fileOperations([
        fileCreateOperation(fileName: "${params.netDriveUsespace}\\Result\\${name}_${scmVars.GIT_COMMIT}.txt", fileContent: "")
    ])
}

/**
 * NetDriveから指定のURLのダウンロードを行う。
 * 
 * @version 1.0
 * 
 * @param String ---
 * 
 * @return なし
 */
void DownloadForNetFolder(){
    echo '[CI/CT] [Information] NetFolder is not supported yet.'
}

/**
 *7zip-extra を使用して、ファイルを圧縮する
 *
 * @version 1.0
 *
 * @param String compressionFilePath 作成する圧縮ファイルのフルパス
 * @param String filePath 圧縮するファイルのフルパス
 *
 * @return
 */
void compressionWith7zip(String compressionFilePath, String filePath){
    //7zip は本ソースが実行されるワークスペースにSVNからチェックアウトされる。
    bat """
        .\\7zip\\7za.exe a "${compressionFilePath}" "${filePath}\\*"
    """
}