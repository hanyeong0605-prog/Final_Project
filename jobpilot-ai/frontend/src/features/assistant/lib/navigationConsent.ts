// 챗봇이 "...페이지로 이동할까요?"라고 물었을 때, 사용자가 친 문장이 수락인지 거절인지
// 아니면 아예 새 질문인지 판정한다.
//
// 2026-09-02: 원래 판정이 "네/응/좋아 (+ 이동해줘)" 정도만 통과하는 통짜 정규식이라
// "ㅇㅇ", "오케이", "그래 그 페이지로 가줘", "네네 부탁해요"처럼 사람이 실제로 치는 말은
// 대부분 못 알아듣고 그대로 서버에 새 질문으로 넘어갔다(= 이동이 안 됨). 그래서 문장
// 전체를 하나의 정규식에 맞추는 대신, 토큰을 하나씩 아래 네 가지로 분류한다.
//
//   AFFIRM  긍정 표현            네, 넵, ㅇㅇ, 오케이, 그래, 좋아요, yes ...
//   MOVE    이동을 뜻하는 서술어  이동해줘, 가자, 열어줘, 보여줘, 부탁해 ...
//   FILLER  뜻 없는 군더더기      그, 거기, 그 페이지로, 바로, 좀, 고마워 ...
//   OTHER   그 외 = 새로운 내용
//
// 수락은 "OTHER 토큰이 하나도 없고 AFFIRM이나 MOVE가 하나 이상"일 때만이다. 이 조건 덕분에
// 표현이 아무리 다양해도 받아주면서, "채용공고 보여줘"처럼 다른 페이지를 가리키는 새 요청은
// (채용공고 = OTHER) 수락으로 오인하지 않고 서버에 질문으로 넘어간다.

export type NavigationIntent = "approve" | "decline" | "unrelated";

// 짧은 대답이 아니라 문장을 쓴 거라면 새 질문으로 본다 - 수락 한마디가 이보다 길 일은 없다.
const MAX_CONSENT_LENGTH = 40;

// 거절은 문장 전체에서 먼저 본다 - "안 갈래", "이동은 안 할래"처럼 부정어가 서술어와
// 떨어져 있어도 잡아야 하고, 거절 문장에는 긍정 토큰("네 근데 괜찮아요")이 섞일 수 있어서
// 수락 판정보다 항상 우선한다.
const DECLINE_PATTERNS: RegExp[] = [
  /(^|\s)(아니+|아니요|아니오|아뇨|아녀|노노|노|no|nope|nah)($|\s)/,
  /(안|못)\s?(가|갈|이동|열|볼|봐|할|해)/,
  /(가|이동|열|보)지\s?\s?마/,
  /(괜찮|됐어|됐습니다|됐다|싫|필요\s?없|나중에|다음에|아직|사양|그만|취소|하지\s?마)/,
];

const AFFIRM =
  /^(네+|넵+|넹+|녜|예+|옙|응+|웅+|엉+|어+|음+|그래+|그램|그럼|그러자|그러죠|그렇게|그렇지|좋아|좋지|좋네|좋습니다|콜|오케이|오케|오키|ㅇ+|ㅇㅋ+|ㅁㅈ|맞아|맞아요|맞습니다|당연|당연하지|yes|yeah|yep|yup|ye|y|sure|ok|okay|okey|k|good)(요|용|여|염)?$/;

const MOVE =
  /^(이동[가-힣]*|가|가자|가줘|가주[가-힣]*|갈래|갈게|갈까|가볼래|가보자|가고[가-힣]*|갑시다|열어[가-힣]*|열자|열어라|보여[가-힣]*|볼래|보고[가-힣]*|부탁[가-힣]*|진행[가-힣]*|해줘|해주[가-힣]*|해줄래|줘|줄래|주세요|주라|주시죠|주시겠어요|고고|ㄱㄱ+|go|plz|please)$/;

const FILLER =
  /^(그|그거|그것|그걸|그리로|그쪽|그쪽으로|거기|거기로|여기|이|이거|해당|해당하는|페이지|페이지로|화면|화면으로|링크|바로|한번|한|좀|일단|그럼|그러면|고마워|고마워요|감사|감사합니다|thanks|thx|저|나|내|것|거|로|으로)$/;

function tokenize(value: string): string[] {
  return value
    .toLowerCase()
    // 문장부호·이모지는 버리고 글자/숫자만 남긴다("ㅇㅇ!!", "네~" 같은 입력 때문에 필요).
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim()
    .split(/\s+/)
    .filter(Boolean);
}

export function readNavigationIntent(raw: string): NavigationIntent {
  const normalized = raw.toLowerCase().replace(/[^\p{L}\p{N}\s]+/gu, " ").replace(/\s+/g, " ").trim();
  if (!normalized) return "unrelated";

  if (DECLINE_PATTERNS.some((pattern) => pattern.test(normalized))) return "decline";
  if (normalized.length > MAX_CONSENT_LENGTH) return "unrelated";

  const tokens = tokenize(raw);
  let positive = 0;
  for (const token of tokens) {
    if (AFFIRM.test(token) || MOVE.test(token)) {
      positive += 1;
      continue;
    }
    if (FILLER.test(token)) continue;
    return "unrelated"; // 군더더기도 긍정도 아닌 = 새로운 내용이 섞여 있다
  }
  return positive > 0 ? "approve" : "unrelated";
}
