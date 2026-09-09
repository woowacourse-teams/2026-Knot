/**
 * `electron-squirrel-startup`은 타입 선언이 없다. Windows Squirrel 설치·업데이트 이벤트
 * (`--squirrel-install` 등)를 처리한 경우 true를 돌려준다(기획서 9.2).
 */
declare module "electron-squirrel-startup" {
  const handledSquirrelEvent: boolean;
  export default handledSquirrelEvent;
}
