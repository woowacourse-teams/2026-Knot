import styled from "@emotion/styled";
import { keyframes } from "@emotion/react";
import type { HTMLAttributes } from "react";

import spinnerMask from "@/assets/spinnerMask.svg";

interface SpinnerProps extends HTMLAttributes<HTMLSpanElement>{
    size?: string;
}

export default function Spinner({size = "1.5rem", ...props}: SpinnerProps) {
    return <Wrapper $size={size} aria-hidden {...props} />;
}

const spin = keyframes`
    to {
        transform: rotate(1turn);
    }
`

const Wrapper = styled.span<{$size: string}>`
    display: block;
    flex-shrink: 0;
    width: ${({$size}) => $size};
    height: ${({$size}) => $size};
    
    background: conic-gradient(from 90deg, currentColor, transparent);

    -webkit-mask: url("${spinnerMask}") center / contain no-repeat;
    mask: url("${spinnerMask}") center / contain no-repeat;

    animation: ${spin} 0.6s linear infinite;
`;