import net.sf.samtools.*;

public class BAMReader {
	public BAMReader(){
	}
	/**
	 * ����Ⱦɫ��ͻ��������з�Χ
	 * @param Chromosome Ⱦɫ����
	 * @param begin ������ʼλ��
	 * @param end ������ֹλ��
	 */
	public BAMReader(String Chromosome, int begin, int end){
		this.Chromosome = Chromosome;
		this.begin = begin;
		this.end = end;
	}
	
	/**
	 * ������з�Χ���ڵ���С��linear index
	 * @return linear index
	 */
	public int getLeftmostLinearIndex(){
		return begin >> 14;
	}
	/**
	 * Ⱦɫ����
	 */
	private String  Chromosome = "";
	/**
	 * ������Χ����ʼλ��
	 */
	private int begin = 0;
	/**
	 * ������Χ����ֹλ��
	 */
	private int end = 0;
}
