import styled from "@emotion/styled";
import { useId, type ReactNode } from "react";
import { createPortal } from "react-dom";

import useDockablePanel from "./model/useDockablePanel";

interface DockablePanelProps {
  /** 트리거 버튼의 접근성 이름 */
  label: string;
  /** 트리거 버튼 안에 그릴 아이콘 */
  icon: ReactNode;
  /** 고정했을 때 패널이 옮겨 갈 자리의 DOM id */
  dockTargetId: string;
  /** 패널에 그릴 내용. 패널의 껍데기(너비·배경·라운드)는 이 내용이 스스로 가져요 */
  children: ReactNode;
}

/**
 * 스치면 띄우고 누르면 자리를 차지하는 패널.
 *
 * 트리거 버튼에 포인터를 얹거나 포커스를 주면 패널이 화면 위에 겹쳐 떠요(드롭다운).
 * 버튼을 누르면 겹치는 대신 `dockTargetId` 자리로 옮겨가 실제로 폭을 차지하고,
 * 다시 누르면 접혀요.
 *
 * 어떤 내용을 담을지는 쓰는 쪽이 정하므로 이 컴포넌트는 도메인을 알지 못해요.
 *
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1364-6863 GNB/Floating}
 * @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1382-2171 Sidebar/Drawer}
 */
export default function DockablePanel({
  label,
  icon,
  dockTargetId,
  children,
}: DockablePanelProps) {
  const panelId = useId();
  const { isDocked, isPeeking, dockTarget, rootProps, triggerProps } =
    useDockablePanel({ dockTargetId });

  return (
    <Root {...rootProps}>
      <Trigger
        type="button"
        aria-label={label}
        aria-controls={panelId}
        aria-expanded={isDocked || isPeeking}
        aria-pressed={isDocked}
        $isDocked={isDocked}
        {...triggerProps}
      >
        {icon}
      </Trigger>

      {isPeeking && <FloatingPanel id={panelId}>{children}</FloatingPanel>}

      {isDocked &&
        dockTarget !== null &&
        createPortal(<DockedPanel id={panelId}>{children}</DockedPanel>, dockTarget)}
    </Root>
  );
}

const Root = styled.div`
  display: flex;
`;

/** @see {@link https://www.figma.com/design/jyDFCKX5AIztZessq4H7nQ/knot?node-id=1364-836 Btn/사이드바} */
const Trigger = styled.button<{ $isDocked: boolean }>`
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.5625rem; /* 9px */
  border: 1px solid
    ${({ theme, $isDocked }) =>
      $isDocked ? theme.neutral[700] : theme.neutral[200]};
  border-radius: 62.4375rem; /* 999px */
  background-color: ${({ theme, $isDocked }) =>
    $isDocked ? theme.neutral[700] : theme.neutral[0]};
  color: ${({ theme, $isDocked }) =>
    $isDocked ? theme.neutral[0] : theme.neutral[800]};
  box-shadow: ${({ theme }) => theme.shadow02};
  transition:
    background-color 0.2s ease-in,
    color 0.2s ease-in;

  &:focus-visible {
    outline: 2px solid ${({ theme }) => theme.sub.accent[500]};
    outline-offset: 2px;
  }
`;

/**
 * 겹쳐 뜬 패널. 화면 왼쪽 위에 고정으로 놓여요.
 *
 * 트리거마다 다른 자리에 붙지 않고 늘 같은 자리에 뜨는 건 디자인이 그렇게 잡혀 있어서예요.
 */
const FloatingPanel = styled.div`
  position: fixed;
  top: 5.5rem; /* 88px */
  bottom: 8.5rem; /* 136px */
  left: 2.5rem; /* 40px */
  z-index: 20;
`;

/** 자리를 차지한 패널. 옮겨 간 자리를 그대로 채워요 */
const DockedPanel = styled.div`
  width: 100%;
  height: 100%;
`;
