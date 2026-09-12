import DesktopOutlined from '@ant-design/icons/DesktopOutlined'
import MoonOutlined from '@ant-design/icons/MoonOutlined'
import SunOutlined from '@ant-design/icons/SunOutlined'
import { Segmented } from 'antd'
import { useTheme, type ThemePreference } from './useTheme'

/**
 * The theme control required by FR-011. The icons are decorative and hidden from assistive technology, so
 * each option is announced as "Light" rather than "sun Light".
 */
export function ThemeToggle() {
  const { preference, setPreference } = useTheme()

  return (
    <Segmented<ThemePreference>
      aria-label="Theme"
      // antd 6.6.3 makes the radio group itself a tab stop, but handles arrow keys only on the radios inside
      // it. A keyboard user's first Tab lands on a stop where the arrows do nothing. Removing the group from
      // the tab order sends focus straight to the checked radio, where they work (FR-015). Verified in
      // Chromium, not only in jsdom.
      tabIndex={-1}
      size="small"
      value={preference}
      onChange={setPreference}
      options={[
        { value: 'system', label: 'System', icon: <DesktopOutlined aria-hidden /> },
        { value: 'light', label: 'Light', icon: <SunOutlined aria-hidden /> },
        { value: 'dark', label: 'Dark', icon: <MoonOutlined aria-hidden /> },
      ]}
    />
  )
}
