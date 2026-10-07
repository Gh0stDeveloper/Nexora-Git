import { CodeIcon, ExternalIcon } from "./icons";

const source = "https://github.com/Gh0stDeveloper/Nexora-Git";

export function SiteHeader() {
  return (
    <header className="sticky top-0 z-50 border-b border-[#21262d] bg-[#010409]/95 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center gap-4 px-4 sm:px-6 lg:px-8">
        <a href="/" className="focus-ring flex min-w-0 items-center gap-3 rounded-md" aria-label="Nexora Git home">
          <img src="/nexora-git-icon.svg" alt="" className="h-8 w-8 rounded-md" />
          <strong className="truncate text-[15px] font-semibold tracking-tight">Nexora Git</strong>
        </a>

        <nav className="ml-auto hidden items-center gap-1 text-sm text-[#c9d1d9] sm:flex" aria-label="Primary navigation">
          <a className="focus-ring rounded-md px-3 py-2 hover:bg-[#21262d] hover:text-white" href="/#features">Features</a>
          <a className="focus-ring rounded-md px-3 py-2 hover:bg-[#21262d] hover:text-white" href="/#contribute">Contribute</a>
          <a className="focus-ring rounded-md px-3 py-2 hover:bg-[#21262d] hover:text-white" href="/terms">Terms</a>
        </nav>

        <a
          className="focus-ring inline-flex items-center gap-2 rounded-md border border-[#30363d] bg-[#21262d] px-3 py-1.5 text-sm font-semibold text-[#f0f6fc] shadow-sm hover:bg-[#30363d]"
          href={source}
          target="_blank"
          rel="noreferrer"
        >
          <CodeIcon />
          <span className="hidden xs:inline">Source</span>
          <ExternalIcon className="h-3.5 w-3.5 text-[#8b949e]" />
        </a>
      </div>
    </header>
  );
}
