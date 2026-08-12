/*
국민내일배움카드 훈련과정 API - 목록
요청 URL
https://www.work24.go.kr/cm/openApi/call/hr/callOpenApiSvcInfo310L01.do


https://www.work24.go.kr/cm/openApi/call/hr/callOpenApiSvcInfo310L01.do?authKey=f6833c3f-c43c-4104-8d10-b890b023b782&returnType=XML&outType=1&pageNum=1&pageSize=20&srchTraStDt=20141001&srchTraEndDt=20141231&srchTraArea1=srchTraProcessNm&crseTracseSe&srchNcs1=20&sort=ASC&sortCol=2

필수
authKey=키
returnType=json
outType=1
pageNum ~1000
pageSize ~100
srchTraStDt 훈련시작일 From
srchTraEndDt 훈련시작일 To  
sort 정렬방법
sortCol 정렬컬럼

선택
srchNcs1 NCS 직종 1차분류 코드 20
crseTracseSe 훈련유형
srchTraProcessNm 훈련과정명




페이지 router >>   /work  
 
*/

export default function HRDPage() {
    return (
        <div>
            <h1> 임시 </h1>
        </div>
    );
}