import styled from "@emotion/styled";

/**
 * 모달 뒤를 덮는 배경 막(스크림).
 *
 * 화면 전체를 Neutral/900 40%로 어둡게 덮고, 넘긴 내용을 그 위 가운데에 둬요.
 * 바깥을 눌렀을 때 할 일 같은 동작은 없어서, 필요하면 쓰는 쪽이 `onClick`으로 붙여요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=2024-11436 Dim}
 */
const Dim = styled.div`
  position: fixed;
  inset: 0;
  z-index: 30; /* 겹쳐 뜨는 패널(20)보다 위 */
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem; /* 16px — 좁은 화면에서도 가장자리에 붙지 않아요 */
  background-color: ${({ theme }) => `${theme.neutral[900]}66`}; /* 40% */
`;

export default Dim;
