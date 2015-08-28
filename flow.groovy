import java.util.Random
def Random rand = new Random()
def int max = 10

def buildVersion = null
stage 'Build'
node('docker') {
    docker.withServer('tcp://docker.jenkins.local:2376') {
        docker.image('uday/maven:3.3.3-jdk-8').inside('-v /data:/data') {
            sh 'rm -rf *'
            checkout([$class: 'GitSCM', branches: [[name: '*/master']], clean: true, doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/uaarkoti/mobile-deposit-api.git']]])
            sh 'git checkout master'
            sh 'git config user.email "uaarkoti@gmail.com"'
            sh 'git config user.name "uaarkoti"'
            sh 'git remote set-url origin git@github.com:uaarkoti/mobile-deposit-api.git'
            sh 'mvn -s /data/mvn/settings.xml -Dmaven.repo.local=/data/mvn/repo clean package'
        }
    }
    archive 'pom.xml, src/, target/'
}

checkpoint 'Build Complete'

stage 'Quality Analysis'
node('docker') {
    unarchive mapping: ['pom.xml' : '.', 'src/' : '.']
    docker.withServer('tcp://docker.jenkins.local:2376') {
        //test in paralell
        parallel(
            integrationTests: {
              docker.image('uday/maven:3.3.3-jdk-8').inside('-v /data:/data') {
                  sh 'mvn -s /data/mvn/settings.xml -Dmaven.repo.local=/data/mvn/repo verify'
              }
            }, sonarAnalysis: {
                docker.image('uday/maven:3.3.3-jdk-8').inside('-v /data:/data') {
                  sh 'echo completed sonar test'
                  //sh 'mvn -s /data/mvn/settings.xml -Dmaven.repo.local=/data/mvn/repo -Dsonar.scm.disabled=True sonar:sonar'
                }
            }, failFast: true
        )
    }
}

checkpoint 'Quality Analysis Complete'
node('docker') {
    //allows randome testing of above checkpoint
    //def failInt = rand.nextInt(max+1)
    //if(failInt>6){
        //error 'error to allow testing checkpoint'
    //}

    unarchive mapping: ['pom.xml' : '.', 'target/' : '.']

    stage 'Version Release'
    def matcher = readFile('pom.xml') =~ '<version>(.+)</version>'
    if (matcher) {
        buildVersion = matcher[0][1]
        echo "Release version: ${buildVersion}"
    }
    matcher = null

    docker.withServer('tcp://docker.jenkins.local:2376', 'slave-docker-us-east-1-tls'){

        stage 'Build Docker Image'
        def mobileDepositApiImage
        dir('target') {
            mobileDepositApiImage = docker.build "uaarkoti/mobile-deposit-api:${buildVersion}"
        }

        stage 'Deploy to Prod'
        try{
          sh "docker stop mobile-deposit-api"
          sh "docker rm mobile-deposit-api"
        } catch (Exception _) {
           echo "no container to stop"
        }
        mobileDepositApiImage.run("--name mobile-deposit-api -p 9090:9090")
        sh 'curl http://docker.jenkins.local:8080/docker-traceability/submitContainerStatus --data-urlencode inspectData="$(docker inspect mobile-deposit-api)" --data-urlencode hostName=prod-server-1'

        stage 'Publish Docker Image'
        //docker.withRegistry('https://registry.hub.docker.com/', 'docker-registry-uaarkoti-login') {
        //    mobileDepositApiImage.push()
        //}
   }
}
