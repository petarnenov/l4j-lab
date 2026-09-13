import PlayCircleOutlined from '@ant-design/icons/PlayCircleOutlined'
import { Alert, Button, Form, Grid, Select, Spin } from 'antd'
import { useEffect, useState } from 'react'
import { useCatalog } from '../hooks/useCatalog'
import { useStartRun } from '../hooks/useStartRun'

/** The company and period controls and the start button. Everything selectable comes from the dataset. */
export function RunLauncher({ onStarted }: { onStarted: (runId: string) => void }) {
  const catalog = useCatalog()
  const narrow = Grid.useBreakpoint().md === false
  const startRun = useStartRun()

  const [companyId, setCompanyId] = useState('')
  const [period, setPeriod] = useState('')

  const companies = catalog.data?.companies ?? []
  const periods = companies.find((c) => c.companyId === companyId)?.periods ?? []

  useEffect(() => {
    if (!companyId && companies.length > 0) setCompanyId(companies[0].companyId ?? '')
  }, [companies, companyId])

  useEffect(() => {
    if (periods.length > 0 && !periods.includes(period)) setPeriod(periods[periods.length - 1])
  }, [periods, period])

  if (catalog.isLoading) return <Spin description="Loading the catalog" />
  if (catalog.isError)
    return <Alert type="error" showIcon title="The catalog could not be loaded." />

  const submit = () =>
    startRun.mutate(
      { companyId, period },
      { onSuccess: (response) => response.runId && onStarted(response.runId) },
    )

  return (
    <Form layout="vertical" aria-label="Start a run" onFinish={submit} requiredMark={false}>
      <div
        style={{
          display: 'flex',
          // Below md the controls stack at full width; from md they share one row.
          flexDirection: narrow ? 'column' : 'row',
          flexWrap: narrow ? 'nowrap' : 'wrap',
          gap: 16,
          alignItems: narrow ? 'stretch' : 'flex-end',
        }}
      >
        <Form.Item
          label="Company"
          htmlFor="launcher-company"
          style={{ marginBottom: 0, flex: '2 1 280px' }}
        >
          <Select
            id="launcher-company"
            value={companyId || undefined}
            onChange={setCompanyId}
            virtual={false}
            // Long company names wrap in the open list rather than being clipped.
            styles={{ popup: { root: { maxWidth: 'calc(100vw - 32px)' } } }}
            optionRender={(option) => (
              <span style={{ whiteSpace: 'normal', overflowWrap: 'anywhere' }}>{option.label}</span>
            )}
            options={companies.map((c) => ({
              value: c.companyId,
              label: c.companyName,
              title: c.companyName,
            }))}
          />
        </Form.Item>

        <Form.Item
          label="Reporting period"
          htmlFor="launcher-period"
          style={{ marginBottom: 0, flex: '1 1 160px' }}
        >
          <Select
            id="launcher-period"
            value={period || undefined}
            onChange={setPeriod}
            virtual={false}
            options={periods.map((p) => ({ value: p, label: p, title: p }))}
          />
        </Form.Item>

        <Form.Item style={{ marginBottom: 0 }}>
          <Button
            type="primary"
            htmlType="submit"
            // Decorative: without aria-hidden the icon's label is announced, and the button's
            // accessible name becomes "play-circle Run the chain".
            icon={<PlayCircleOutlined aria-hidden />}
            loading={startRun.isPending}
            disabled={!companyId || !period}
          >
            Run the chain
          </Button>
        </Form.Item>
      </div>

      {startRun.isError && (
        <Alert
          style={{ marginTop: 16 }}
          type="error"
          showIcon
          title={(startRun.error as Error).message}
        />
      )}
    </Form>
  )
}
