import Cafe from '../../components/Cafe';

export default async function MinimalPage({ searchParams }: {
  searchParams: Promise<{ activity?: string | string[] }>;
}) {
  const { activity } = await searchParams;
  return <Cafe variant="minimal" initialActivity={activity === 'game' ? 'game' : null} />;
}
